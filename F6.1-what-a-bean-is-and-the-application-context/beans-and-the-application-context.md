# F6.1 — What a bean is, and what the application context does

Notes for Task F6.1. One lesson per section. Each lesson ends with its understanding-check
questions and full answers.

**Prerequisite machine state:** Java 21 (`java -version` → `openjdk version "21...`). That is
all Lessons 1–3 need. From Lesson 4 onwards you also need Maven 3.9.x — one copy sits
unpacked at `F5.10-java-talks-to-postgres-jdbc-and-jdbcclient/tools/apache-maven-3.9.9` —
plus the scratch Spring project that Lesson 4 itself creates.

## The task's subtasks

| Id | Subtask |
|----|---------|
| S1 | In a scratch project, wire a controller entirely by hand: `new EventService(new JdbcEventRepository(dataSource))`. It works. Keep it. |
| S2 | Try to test that controller with a fake repository. Fail. Write one sentence in `NOTES.md` about what stopped you. |
| S3 | Convert: `@Service`, `@Repository`, constructor parameters. Delete every `new`. The app still answers. |
| S4 | Add the `BeanLister` class. Print every bean name. |
| S5 | Read the list. Find your own three classes. Notice the ~200 names you never wrote. |
| S6 | Break it: move `EventService` to a sibling package. Read the whole failure. Move it back. |
| S7 | Learn the rule: component scanning starts at the main class's package and goes downwards only. |
| S8 | Compare `@Component`, `@Service`, `@Repository`. |
| S9 | Ask AI: bean definition vs bean instance, and when one becomes the other. |
| S10 | Ask AI: three reasons a `@Service` class still fails to become a bean, with each error message. |
| CP | Checkpoint: four questions answered out loud. |

## Roadmap for this task — 17 small lessons

| # | Lesson | What it gives you |
|---|--------|-------------------|
| 1 | The dependency chain | What a dependency is. Why objects must be built in order. |
| 2 | The hidden `new` trap | Why a class that builds its own helper locks that choice in forever. |
| 3 | A fake behind an interface | How one interface lets a real helper and a fake helper swap places. |
| 4 | Build it wrong in Spring *(S1)* | A working controller that wires everything by hand. The "before" picture. |
| 5 | Feel the block *(S2)* | Try to hand it a fake. Fail. Write the sentence in `NOTES.md`. |
| 6 | Dependency injection by hand | Move every `new` to one place. That one place is a hand-made container. |
| 7 | What a bean is | One sentence you can say out loud: an object Spring built and keeps. |
| 8 | The application context | The box that holds every bean. A real object, not an idea. |
| 9 | Stereotype annotations | The marks (`@Component`, `@Service`, `@Repository`, `@RestController`) that say "build me". |
| 10 | Constructor wiring | How Spring reads a constructor and hands in the beans it asks for. |
| 11 | Convert the scratch project *(S3)* | Annotations on, every `new` deleted, app still answers. |
| 12 | Bean definition vs bean instance *(S9)* | The recipe vs the cooked dish. The two phases of startup. |
| 13 | Component scanning and the downward rule *(S7)* | Where Spring looks for classes, and where it never looks. |
| 14 | Print every bean *(S4, S5)* | `BeanLister` shows the whole container. Find your three classes in it. |
| 15 | Break it: the package trap *(S6)* | Cause the classic startup failure, read it line by line, fix it. |
| 16 | `@Repository` vs `@Component` *(S8)* | What the different names mean, and the one real extra behaviour. |
| 17 | Failure drills and checkpoint *(S10, CP)* | Three ways `@Service` still fails, then the four checkpoint answers. |

---

## Lesson 1 — The dependency chain

### Definition

A **dependency** is an object that another object needs to do its job. When object A needs
object B, and object B needs object C, that is a **dependency chain**.

### Why it is needed

One giant class that reads the database, applies business rules, and answers web requests
would be huge, and no part of it could change without touching the rest. So the work is
split into small classes, one job each. But now the classes need each other: a service
cannot get events out of nowhere, it needs a repository object to fetch them. "Needing
another object" is the price of small, focused classes. That need is the dependency.

### How it works, step by step

A class says what it needs through its **constructor** — the special method that runs when
an object is built with `new`. Three steps:

1. The class declares a field for the helper it needs.
2. The constructor takes the helper as a parameter and stores it in the field.
3. Whoever builds the object must build the helper **first**, then hand it in.

So the deepest object in the chain is built first. The chain is built bottom-up.

### Example

File: `scratch/lesson01/Lesson1.java`. `EventService` needs `EventRepository`; `main`
builds them in order.

```java
import java.util.List;

class EventRepository {
    List<String> findAllNames() {
        // Pretend this line talks to a database.
        return List.of("Rock Night", "Jazz Evening");
    }
}

class EventService {
    private final EventRepository repository;

    EventService(EventRepository repository) {
        this.repository = repository;
    }

    List<String> upcomingEventNames() {
        return repository.findAllNames();
    }
}

public class Lesson1 {
    public static void main(String[] args) {
        EventRepository repository = new EventRepository();   // step 1: build the helper
        EventService service = new EventService(repository);  // step 2: hand it in
        System.out.println(service.upcomingEventNames());     // step 3: use it
    }
}
```

Real output:

```text
[Rock Night, Jazz Evening]
```

### Walkthrough

- **Step 1:** `new EventRepository()` builds the repository. It needs nothing, so it can be
  built first.
- **Step 2:** `new EventService(repository)` builds the service. The constructor receives
  the repository and stores it in the field `this.repository`. From now on the service
  carries its helper inside it.
- **Step 3:** `service.upcomingEventNames()` runs. Inside, it calls
  `repository.findAllNames()`. The service does not know how the names are found. It only
  knows the repository can find them.
- Key observation: `EventService` never writes `new EventRepository()` anywhere. The
  repository came in from outside, through the constructor. The whole task hangs on this
  detail.

### Important terms

| Term | Meaning |
| --- | --- |
| **Dependency** | An object another object needs to do its job. |
| **Dependency chain** | A needs B, B needs C: a line of needs, built deepest-first. |
| **Constructor** | The special method that runs when an object is built with `new`. |
| **Field** | A variable that lives inside an object and travels with it. |
| **Build order** | Deepest object first; each object's helpers must exist before it does. |

### Common mistakes

**Mistake A — forgetting to hand the helper in.** `new EventService()` with empty brackets
does not compile:

```text
MistakeA.java:25: error: constructor EventService in class EventService cannot be applied to given types;
        EventService service = new EventService();  // step 2: hand it in
                               ^
  required: EventRepository
  found:    no arguments
```

| Error part | Plain meaning |
|---|---|
| `required: EventRepository` | "This constructor needs one `EventRepository`." |
| `found: no arguments` | "You gave me nothing." |

The constructor is a contract: no helper, no object.

**Mistake B — handing in `null`.** `new EventService(null)` compiles and starts. It dies
later, at the moment of use:

```text
Exception in thread "main" java.lang.NullPointerException: Cannot invoke "EventRepository.findAllNames()" because "this.repository" is null
	at EventService.upcomingEventNames(MistakeB.java:18)
	at MistakeB.main(MistakeB.java:26)
```

| Error part | Plain meaning |
|---|---|
| `Cannot invoke "EventRepository.findAllNames()"` | "I tried to call a method on the repository." |
| `because "this.repository" is null` | "The field holds nothing. Nobody put a real object there." |

Mistake A fails early and loudly. Mistake B fails late and quietly. Early and loud is
better. Spring is built around that preference.

### Where it is used

Every real backend is a longer version of this chain. A controller (answers web requests)
needs a service (applies business rules). The service needs a repository (talks to the
database). The repository needs a datasource (holds the actual connection). Something must
build all four, deepest first, and hand each one to the next.

### Link to the task

This is the skeleton of subtask **S1**: "build the chain by hand: a controller that does
`new EventService(new JdbcEventRepository(dataSource))`". In Lesson 1 the chain has two
links and lives in `main`. In Lesson 4 the same chain grows to four links and lives in a
controller. Nothing new happens there except length.

### Do it yourself (second laptop)

**Setup check:** run `java -version`. Expect a line starting with `openjdk version "21`. If
the command is not found: `brew install openjdk@21`, then
`sudo ln -sfn $(brew --prefix)/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk`,
then check again.

**Step 1** — make the folder and enter it:

```bash
mkdir -p F6.1-what-a-bean-is-and-the-application-context/scratch/lesson01
cd F6.1-what-a-bean-is-and-the-application-context/scratch/lesson01
```

**Step 2** — create `Lesson1.java` with exactly the content shown in the Example section of
this lesson (the full file is printed there).

**Step 3** — compile and run:

```bash
javac Lesson1.java && java Lesson1
```

Expect: `[Rock Night, Jazz Evening]`.

**Step 4 (failure drill)** — change `new EventService(repository)` to `new EventService()`.
Run `javac Lesson1.java`. Expect the error `required: EventRepository / found: no arguments`.
Change it back, compile, run, see the list again.

**Done when:** you have seen both the good output and the compile error, and you can say in
one sentence why the repository must be built before the service.

### Summary for revision

- Dependency = an object another object needs. Chain = needs stacked on needs.
- A class asks for its helpers through constructor parameters and stores them in fields.
- Build order is deepest-first: helpers exist before the objects that need them.
- The example class never builds its own helper; the helper arrives from outside.
- Missing helper → compile error (early, loud). `null` helper → `NullPointerException` at
  first use (late, quiet). Early and loud is better.

### Check questions, with answers

**Question 1.** Here is a complete program:

```java
class NameFormatter {
    String shout(String name) {
        return name.toUpperCase() + "!";
    }
}

class Greeter {
    private final NameFormatter formatter;

    Greeter(NameFormatter formatter) {
        this.formatter = formatter;
    }

    String greet(String name) {
        return "Hello " + formatter.shout(name);
    }
}

public class Q1 {
    public static void main(String[] args) {
        NameFormatter formatter = new NameFormatter();
        Greeter greeter = new Greeter(formatter);
        System.out.println(greeter.greet("ava"));
    }
}
```

(a) Why must `new NameFormatter()` run before `new Greeter(...)`? (b) What exactly does this
program print?

**Answer.** (a) `Greeter`'s constructor demands a `NameFormatter` as a parameter. You cannot
call that constructor until a `NameFormatter` object exists to hand in. The helper is built
first; the chain is built deepest-first. (b) `greet("ava")` calls `shout("ava")`, which
returns `"AVA!"`; so it prints exactly:

```text
Hello AVA!
```

(Compiled and run to confirm.)

**Question 2.** Here is one class on its own:

```java
class EventService {
    private final EventRepository repository;

    EventService(EventRepository repository) {
        this.repository = repository;
    }

    List<String> upcomingEventNames() {
        return repository.findAllNames();
    }
}
```

This class never contains `new EventRepository()` anywhere. When the program runs, where
does its repository object come from?

**Answer.** From outside. Whoever builds the `EventService` must first build an
`EventRepository` (or receive one from somewhere else), then pass it into the constructor.
The constructor stores it in the field `this.repository`, and every method of the service
uses that stored object. The service's job is to *use* the repository, never to *create* it.

---

## Lesson 2 — The hidden `new` trap

### Definition

The hidden `new` trap is when a class builds its own helper with `new` **inside itself**.
The choice of helper is then locked inside that class. Nobody outside can change it.

### Why it is needed

This trap is the disease; Spring and its container are the cure. Without seeing the
disease, the cure looks like ceremony. The nasty part: the trap produces **no error**. The
code compiles, runs, and gives correct output. The damage only shows the day you want the
class to work with a *different* helper. Then you find there is no door.

### How it works, step by step

Two styles of the same class, one line of difference.

**Style 1 — helper handed in:**

```java
class EventService {
    private final EventRepository repository;

    EventService(EventRepository repository) {  // door: the helper comes in here
        this.repository = repository;
    }
}
```

**Style 2 — helper built inside (the trap):**

```java
class EventService {
    private final EventRepository repository =
            new EventRepository("jdbc:postgresql://localhost:5432/zenith");  // no door
}
```

| Question | Style 1: handed in | Style 2: built inside |
|---|---|---|
| Who chooses the helper? | The caller, each time it builds the service. | The class itself, once, forever. |
| Can a caller change the choice? | Yes. Build a different helper, hand it in. | No. There is nothing to hand in. |
| What does the constructor say? | "Give me an `EventRepository`." | Nothing. Empty brackets. |
| Where does configuration live? | Outside, at the build site. | Hardcoded inside the class. |

The lock mechanism: the `new` runs in a field of the class, during construction, before
any caller gets a say. The constructor offers no parameter, so nothing can be passed in.

### Example

File: `scratch/lesson02/Lesson2.java`. The repository takes a database address, so the
locked-in choice shows up in the output.

```java
import java.util.List;

class EventRepository {
    private final String databaseUrl;

    EventRepository(String databaseUrl) {
        this.databaseUrl = databaseUrl;
    }

    List<String> findAllNames() {
        // Pretend this line queries the database at databaseUrl.
        return List.of("names fetched from " + databaseUrl);
    }
}

class EventService {
    // The trap: the service builds its own helper. The choice is baked in.
    private final EventRepository repository =
            new EventRepository("jdbc:postgresql://localhost:5432/zenith");

    List<String> upcomingEventNames() {
        return repository.findAllNames();
    }
}

public class Lesson2 {
    public static void main(String[] args) {
        EventService service = new EventService();
        System.out.println(service.upcomingEventNames());
    }
}
```

Real output:

```text
[names fetched from jdbc:postgresql://localhost:5432/zenith]
```

It works. That is the point. The trap looks like success.

### Walkthrough

- `main` runs `new EventService()`. The call looks **easy**: empty brackets, no setup.
  That ease is why beginners love the trap.
- During construction the field line runs:
  `new EventRepository("jdbc:postgresql://localhost:5432/zenith")`. The service builds its
  own helper, address hardcoded.
- `main` never saw the repository or the address. Both were decided inside `EventService`.
- Now `main` wants a second service reading the test database. Attempt:
  `new EventService("jdbc:postgresql://localhost:5432/zenith_test")`. Real result:

```text
TryToChange.java:28: error: constructor EventService in class EventService cannot be applied to given types;
        EventService service = new EventService("jdbc:postgresql://localhost:5432/zenith_test");
                               ^
  required: no arguments
  found:    String
```

| Error part | Plain meaning |
|---|---|
| `required: no arguments` | "This constructor accepts nothing." |
| `found: String` | "You tried to pass something in. There is no door for it." |

Compare with Lesson 1's error (`required: EventRepository`): there the constructor demanded
its helper; here it refuses all input. Same compiler, opposite disease.

- The **only** way to repoint this service is to edit the hardcoded string inside
  `EventService.java` and recompile. Changing configuration now means changing source code.

### Important terms

| Term | Meaning |
| --- | --- |
| **Hidden `new` trap** | A class builds its own helper inside itself; the choice is locked in. |
| **Door** | A constructor parameter: the only way a caller can hand a helper in. |
| **Call site** | The place that writes `new EventService(...)` — where the choice *should* live. |
| **Hardcoded** | A value written directly into source code; changing it means editing the class. |

### Common mistakes

- **"It works, so it is fine."** Working and changeable are different properties. This code
  works and is frozen.
- **"So every `new` is evil."** No. `new ArrayList<>()` inside a method is harmless: a list
  is a plain data holder and will never need swapping. The trap is about helpers that do
  real work — database, network, clock — exactly the things you will one day swap.
- **The half-trap.** A class takes one helper through the constructor but quietly builds a
  second one inside. The constructor looks honest; the class still lies.

### Where it is seen

Everywhere. It is the most natural first draft ("my service needs a repository, so I'll
make one right here"). Legacy codebases have layers of it. The lock is discovered the first
time someone writes a test or points one copy of the app at a different database.

### Link to the task

Subtask **S1** builds this trap deliberately, in Spring, inside a controller:
`new EventService(new JdbcEventRepository(dataSource))`. Subtask **S2** then makes you feel
the lock: you try to hand the controller a fake repository and find there is no door.

### Do it yourself (second laptop)

**Setup check:** `java -version` must show 21. If missing: `brew install openjdk@21`, then
`sudo ln -sfn $(brew --prefix)/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk`.

**Step 1** — folder:

```bash
mkdir -p F6.1-what-a-bean-is-and-the-application-context/scratch/lesson02
cd F6.1-what-a-bean-is-and-the-application-context/scratch/lesson02
```

**Step 2** — create `Lesson2.java` with exactly the content shown in the Example section of
this lesson (the full file is printed there).

**Step 3** — compile and run: `javac Lesson2.java && java Lesson2`.
Expect: `[names fetched from jdbc:postgresql://localhost:5432/zenith]`.

**Step 4 (failure drill)** — in `main`, change `new EventService()` to
`new EventService("jdbc:postgresql://localhost:5432/zenith_test")`. Compile. Expect:
`required: no arguments / found: String`. Say out loud what it means: the class has no
door. Change `main` back.

**Step 5 (the only escape)** — edit the hardcoded string inside `EventService` to end with
`zenith_test`, recompile, run. The output changes. You changed configuration by editing
source code: that is the cost of the trap. Change it back.

**Done when:** you can point at the exact line that locks the choice, and say why `main`
cannot override it.

### Summary for revision

- The trap: a class builds its own helper with `new` inside itself.
- It produces no error. It fails silently, later, at change time — not at run time.
- The constructor with empty brackets is the tell: no parameter means no door.
- The only escape without redesign is editing source code and recompiling.
- Harmless `new`: plain data holders (`ArrayList`). Dangerous `new`: helpers that do real
  work (database, network, clock).

### Check questions, with answers

**Question 1.** Here are three complete classes. `SmtpClient` is a helper that sends mail.
`MailSenderA` and `MailSenderB` are two versions of the same idea:

```java
class SmtpClient {
    private final String host;
    private final int port;

    SmtpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    void deliver(String text) {
        System.out.println("sending via " + host + ":" + port + " -> " + text);
    }
}

class MailSenderA {
    private final SmtpClient client;

    MailSenderA(SmtpClient client) {
        this.client = client;
    }

    void send(String text) {
        client.deliver(text);
    }
}

class MailSenderB {
    private final SmtpClient client = new SmtpClient("smtp.company.com", 587);

    void send(String text) {
        client.deliver(text);
    }
}
```

(a) Which version lets the caller decide which mail server is used? (b) In the other
version, name the exact line that locks the choice.

**Answer.** (a) `MailSenderA`. The caller builds the `SmtpClient` — any host, any port —
and hands it in: `new MailSenderA(new SmtpClient("smtp.test.local", 2525))`. (b) In
`MailSenderB` the locking line is the field:
`private final SmtpClient client = new SmtpClient("smtp.company.com", 587);`. It runs
during construction, before any caller gets a say, and `MailSenderB` has no constructor
parameter to override it.

**Question 2.** Here is a complete program:

```java
class PdfWriter {
    private final String folder;

    PdfWriter(String folder) {
        this.folder = folder;
    }

    void write(String name) {
        System.out.println("writing " + folder + "/" + name + ".pdf");
    }
}

class ReportService {
    private final PdfWriter writer = new PdfWriter("/reports/final");

    void monthlyReport() {
        writer.write("october");
    }
}

public class Q2 {
    public static void main(String[] args) {
        ReportService service = new ReportService();
        service.monthlyReport();
    }
}
```

You want one run where reports go to `/reports/draft` instead, **without editing the
`ReportService` class**. (a) Is that possible with the code as written? Why or why not?
(b) What single change to `ReportService` would make it possible?

**Answer.** (a) Not possible. The folder choice lives inside `ReportService`, in the field
line `new PdfWriter("/reports/final")`. `ReportService` has no constructor parameter, so
`main` has no door to pass anything through. (b) Give `ReportService` a door — take the
writer as a constructor parameter:

```java
class ReportService {
    private final PdfWriter writer;

    ReportService(PdfWriter writer) {
        this.writer = writer;
    }

    void monthlyReport() {
        writer.write("october");
    }
}
```

Then `main` chooses: `new ReportService(new PdfWriter("/reports/draft"))`.

---

## Lesson 3 — A fake behind an interface

### Definition

An **interface** is a written list of method promises, with no method bodies. An
**implementation** is a class that keeps every promise on that list. When a class asks for
its helper *by interface*, any implementation fits — including a fake one written for
testing.

### Why it is needed

Lesson 2 showed one lock: the helper chosen *inside* the class. There is a second, quieter
lock. Even when the helper comes in through a constructor parameter, the parameter's *type*
decides what fits through the door. If the type is the concrete class
`JdbcEventRepository`, only a `JdbcEventRepository` fits; a fake cannot enter. The task's
sentence "test with a repository that returns two fake events and never touches a database"
is impossible unless the service asks for an interface.

### How it works, step by step

Analogy: a wall socket does not care whether a lamp or a charger is plugged in; it cares
about the plug shape. The interface is the plug shape; each implementation is an appliance.

1. Write the interface: just the promise list.
2. Write the real implementation: keeps the promises by doing real work.
3. Write the fake implementation: keeps the same promises by returning hardcoded data.
4. The service's field and constructor parameter use the **interface type**.
5. The caller decides which implementation to hand in. The service never knows which one
   it got.

### Example

File: `scratch/lesson03/Lesson3.java`. One interface, two implementations, one service,
and a `main` that builds the service twice — once real, once fake.

```java
import java.util.List;

interface EventRepository {
    List<String> findAllNames();
}

class JdbcEventRepository implements EventRepository {
    private final String databaseUrl;

    JdbcEventRepository(String databaseUrl) {
        this.databaseUrl = databaseUrl;
    }

    @Override
    public List<String> findAllNames() {
        // Pretend this line queries the database at databaseUrl.
        return List.of("names fetched from " + databaseUrl);
    }
}

class FakeEventRepository implements EventRepository {
    @Override
    public List<String> findAllNames() {
        return List.of("Fake Gig", "Fake Festival");
    }
}

class EventService {
    private final EventRepository repository;   // the interface, not a class

    EventService(EventRepository repository) {
        this.repository = repository;
    }

    List<String> upcomingEventNames() {
        return repository.findAllNames();
    }
}

public class Lesson3 {
    public static void main(String[] args) {
        EventService real = new EventService(
                new JdbcEventRepository("jdbc:postgresql://localhost:5432/zenith"));
        System.out.println("real: " + real.upcomingEventNames());

        EventService test = new EventService(new FakeEventRepository());
        System.out.println("test: " + test.upcomingEventNames());
    }
}
```

Real output:

```text
real: [names fetched from jdbc:postgresql://localhost:5432/zenith]
test: [Fake Gig, Fake Festival]
```

Syntax notes: `implements EventRepository` means "this class keeps that promise list".
`@Override` marks a method as keeping one of the promises; the compiler checks the promise
really exists.

### Walkthrough

- `interface EventRepository` makes one promise: anyone implementing me can do
  `findAllNames()` and return a list of strings. Nothing about *how*.
- `JdbcEventRepository` keeps the promise by (pretending to) ask a database.
- `FakeEventRepository` keeps it by returning two hardcoded names. No database. Instant.
  Predictable.
- `EventService` stores its helper in a field typed by the **interface**, and its
  constructor takes the interface. The service can only call what the promise list offers,
  so any keeper of the promises works.
- First build: real repository in → database names out. Second build: fake in → fake names
  out.
- `EventService` is **byte-for-byte identical** in both runs. Only the caller's choice
  changed. One class, two behaviours, chosen from outside. That is the whole trick.

### Important terms

| Term | Meaning |
| --- | --- |
| **Interface** | A written list of method promises, with no bodies. |
| **Implementation** | A class that keeps every promise on the list (`implements X`). |
| **`@Override`** | A mark on a method: "this keeps one of the promises"; compiler-checked. |
| **Fake** | An implementation returning hardcoded data, for tests. Dumb on purpose. |
| **Depend on the interface** | Field and constructor parameter use the interface type, never the concrete class. |

### Common mistakes

**Mistake A — asking for the concrete class instead of the interface.** With field and
constructor parameter typed `JdbcEventRepository`, handing in the fake fails:

```text
ConcreteTrap.java:46: error: incompatible types: FakeEventRepository cannot be converted to JdbcEventRepository
        EventService test = new EventService(new FakeEventRepository());
                                             ^
```

| Error part | Plain meaning |
|---|---|
| `incompatible types` | "The thing you passed does not fit the declared type." |
| `FakeEventRepository cannot be converted to JdbcEventRepository` | "The door is shaped for one specific class, and the fake is a different class." |

Both classes keep the same promises, but the door was shaped for one appliance instead of
the plug. Rule: depend on the **interface**.

**Mistake B — thinking the service knows which helper it got.** It cannot. It sees only
the promise list. That ignorance is why the service never needs editing when the helper
changes.

**Mistake C — writing a clever fake.** A fake should be dumb: fixed data, no logic.
A clever fake means your tests start testing the fake.

### Where it is used

Every serious test suite. Real repository → Postgres; fake → canned rows in a microsecond.
Payment service: real card charger in production, pretend charger in tests. Seat-hold
service needing the current time: real clock in production, frozen clock in tests so
"expires in 5 minutes" is testable without waiting. Real work behind an interface, fakes in
tests: the standard shape of a testable backend.

### Link to the task

Subtask **S2**: test the controller "with a repository that returns two fake events and
never touches a database" — that repository is exactly `FakeEventRepository` (`Fake Gig`,
`Fake Festival`). The swap worked here because `EventService`'s door is shaped like an
interface. Lessons 4–5 build the Spring version where the controller hides a `new` inside
itself, and this same swap becomes impossible. Both halves are now known: the trap
(Lesson 2) and what the trap takes away (this lesson).

### Do it yourself (second laptop)

**Setup check:** `java -version` must show 21. If missing: `brew install openjdk@21`, then
`sudo ln -sfn $(brew --prefix)/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk`.

**Step 1** — folder:

```bash
mkdir -p F6.1-what-a-bean-is-and-the-application-context/scratch/lesson03
cd F6.1-what-a-bean-is-and-the-application-context/scratch/lesson03
```

**Step 2** — create `Lesson3.java` with exactly the content shown in the Example section
of this lesson (the full file is printed there).

**Step 3** — compile and run: `javac Lesson3.java && java Lesson3`. Expect both lines:

```text
real: [names fetched from jdbc:postgresql://localhost:5432/zenith]
test: [Fake Gig, Fake Festival]
```

**Step 4 (failure drill)** — inside `EventService`, change the field type and the
constructor parameter type from `EventRepository` to `JdbcEventRepository`. Compile.
Expect: `incompatible types: FakeEventRepository cannot be converted to JdbcEventRepository`.
Say why out loud: the door is now shaped for one class, and the fake is a different class.
Change both back, compile, run, see both lines again.

**Done when:** both output lines print, you have caused and fixed the `incompatible types`
error, and you can explain why `EventService` did not change between the real run and the
fake run.

### Summary for revision

- Interface = promise list, no bodies. Implementation = promise keeper.
- The service's field and constructor parameter use the interface type. That shapes the
  door like a plug, not like one appliance.
- The caller picks the implementation; the service never knows which one it holds.
- The fake keeps the same promises with hardcoded data. Dumb on purpose.
- Concrete parameter type → `incompatible types` the moment a fake is offered.

### Check questions, with answers

**Question 1.** Here is a complete program:

```java
interface PriceSource {
    int priceFor(String item);
}

class CatalogPriceSource implements PriceSource {
    @Override
    public int priceFor(String item) {
        // Pretend this looks the price up in a database.
        return 500;
    }
}

class FixedPriceSource implements PriceSource {
    @Override
    public int priceFor(String item) {
        return 100;
    }
}

class CheckoutService {
    private final PriceSource prices;

    CheckoutService(PriceSource prices) {
        this.prices = prices;
    }

    int totalFor(String item, int quantity) {
        return prices.priceFor(item) * quantity;
    }
}

public class Q1 {
    public static void main(String[] args) {
        CheckoutService checkout = new CheckoutService(new FixedPriceSource());
        System.out.println(checkout.totalFor("ticket", 3));
    }
}
```

(a) What exactly does this program print? (b) Which line decided that `FixedPriceSource`
would be used — a line inside `CheckoutService`, or a line inside `main`?

**Answer.** (a) It prints `300`. The service holds a `FixedPriceSource`, so
`priceFor("ticket")` returns `100`, and `100 * 3 = 300`. (Compiled and run to confirm.)
(b) A line inside `main`: `new CheckoutService(new FixedPriceSource())`. Inside
`CheckoutService` there is no line that picks an implementation — its field and parameter
are typed by the interface `PriceSource`. The choice lives entirely at the call site.

**Question 2.** Here is a complete program. Look closely at the types inside
`SignupService`:

```java
interface Notifier {
    void notifyUser(String message);
}

class EmailNotifier implements Notifier {
    @Override
    public void notifyUser(String message) {
        System.out.println("emailing: " + message);
    }
}

class SilentNotifier implements Notifier {
    @Override
    public void notifyUser(String message) {
        // does nothing, on purpose (for tests)
    }
}

class SignupService {
    private final EmailNotifier notifier;   // look closely at this type

    SignupService(EmailNotifier notifier) {
        this.notifier = notifier;
    }

    void register(String user) {
        notifier.notifyUser("welcome " + user);
    }
}

public class Q2 {
    public static void main(String[] args) {
        SignupService service = new SignupService(new SilentNotifier());
        service.register("ava");
    }
}
```

(a) Does this compile? If not, what will the compiler complain about? (b) What should
`SignupService` ask for instead, so that both notifiers fit?

**Answer.** (a) It does not compile. Real error:

```text
Q2L3.java:33: error: incompatible types: SilentNotifier cannot be converted to EmailNotifier
        SignupService service = new SignupService(new SilentNotifier());
                                                  ^
```

`SignupService`'s door is shaped for the concrete class `EmailNotifier`. A
`SilentNotifier` is not an `EmailNotifier`, even though both keep the `Notifier` promises.
(b) Change the field type and the constructor parameter type to the interface `Notifier`:

```java
class SignupService {
    private final Notifier notifier;

    SignupService(Notifier notifier) {
        this.notifier = notifier;
    }

    void register(String user) {
        notifier.notifyUser("welcome " + user);
    }
}
```

Now `new SignupService(new EmailNotifier())` and `new SignupService(new SilentNotifier())`
both compile. The door is shaped like the plug, not like one appliance.

---

## Lesson 4 — Build it wrong in Spring (subtask S1)

### Definition

A **scratch project** is a small throwaway project built only for learning. This one is a
real Spring Boot web application with a four-link chain: controller → service → repository
→ database connection. It is **wrong on purpose**: the controller builds its entire chain
by hand, with `new`, inside itself. The task says: build it, see it work, keep it.

### Why it is needed

Spring exists to replace something. Until that something has been built by hand, Spring
looks like magic and its errors read like noise. This app is the "before" picture; every
later lesson improves it.

### Reminders used by this lesson

| Piece | One-line reminder |
|---|---|
| `@SpringBootApplication` | Mark on the main class: "start Spring here, and start the web server". |
| `@RestController` | Mark on a class: "Spring, build one of these, and send matching web requests to it". |
| `@GetMapping("/events")` | Mark on a method: "run this method for GET requests to /events". |
| `DataSource` | A tap for database connections: `getConnection()` hands a live line to Postgres. |
| `HikariDataSource` | The standard `DataSource`: keeps a pool of ready connections and lends them out. |
| `Connection`, `Statement`, `ResultSet` | Plain JDBC: open a line, send SQL text, walk the returned rows. |

The shape of the wrong version: Spring does exactly two jobs — runs the web server and
builds the controller (it must, or it could not deliver requests to it). **Everything
below the controller is wired by hand** inside the controller's constructor.

### The project — six files in `scratch-app/`

`pom.xml` — Spring Boot 3.5.16, three dependencies (web, Hikari pool, Postgres driver):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.16</version>
        <relativePath/>
    </parent>

    <groupId>com.zenith</groupId>
    <artifactId>catalog-scratch</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <!-- Connection pool, used BY HAND in the wrong version. -->
        <dependency>
            <groupId>com.zaxxer</groupId>
            <artifactId>HikariCP</artifactId>
        </dependency>
        <!-- Postgres driver. -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

`src/main/java/com/zenith/catalog/CatalogApplication.java` — the start button:

```java
package com.zenith.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
```

`EventRepository.java` — the promise list:

```java
package com.zenith.catalog;

import java.util.List;

public interface EventRepository {
    List<String> findAllNames();
}
```

`JdbcEventRepository.java` — plain JDBC against the `event` table from module F5:

```java
package com.zenith.catalog;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

public class JdbcEventRepository implements EventRepository {

    private final DataSource dataSource;

    public JdbcEventRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<String> findAllNames() {
        List<String> names = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT name FROM event ORDER BY starts_at")) {
            while (rows.next()) {
                names.add(rows.getString("name"));
            }
            return names;
        } catch (SQLException e) {
            throw new RuntimeException("query failed", e);
        }
    }
}
```

`EventService.java` — asks for the interface:

```java
package com.zenith.catalog;

import java.util.List;

public class EventService {

    private final EventRepository repository;

    public EventService(EventRepository repository) {
        this.repository = repository;
    }

    public List<String> upcomingEventNames() {
        return repository.findAllNames();
    }
}
```

`EventController.java` — **the wrong part**; count the three `new`s:

```java
package com.zenith.catalog;

import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventController {

    private final EventService service;

    public EventController() {
        // WRONG ON PURPOSE: the controller builds its whole chain by hand.
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:postgresql://localhost:5432/zenith");
        dataSource.setUsername("postgres");
        dataSource.setPassword("dev");
        this.service = new EventService(new JdbcEventRepository(dataSource));
    }

    @GetMapping("/events")
    public List<String> events() {
        return service.upcomingEventNames();
    }
}
```

Real startup log (trimmed):

```text
 :: Spring Boot ::               (v3.5.16)
... TomcatWebServer  : Tomcat started on port 8080 (http) with context path '/'
... CatalogApplication : Started CatalogApplication in 0.501 seconds
```

Real request and answer, from the real `zenith` database:

```text
$ curl http://localhost:8080/events
["Monsoon Rock Night","Jazz at the Note","Classical Evening","Open Mic Night","Sufi Night","Blues Brunch","Indie Fest"]
HTTP 200
```

### Walkthrough

**At startup:** Maven compiles and runs `CatalogApplication.main`. Spring starts, sees
`@RestController`, and builds **one** controller object — the only object of yours Spring
builds here. Building it runs the constructor: the three `new`s fire deepest-first (tap →
repository → service) and the chain lands in the `service` field. Tomcat listens on 8080.
**No database contact yet** — the tap is configured, not opened.

**At the first request:** Spring routes GET `/events` to `events()` (because of
`@GetMapping`). Service → repository → `dataSource.getConnection()` — now the first real
connection opens. SQL runs, seven rows return, Spring turns the list into JSON.

### Common mistakes

**Mistake A — database container not running.** The app **starts perfectly** (nothing
touches the database at startup); the failure waits for the first request, then takes ~30
seconds (the pool retries that long). Caused for real:

```text
$ curl http://localhost:8080/events
{"timestamp":"2026-10-28T19:21:52.848+00:00","status":500,"error":"Internal Server Error","path":"/events"}
HTTP 500
```

App log:

```text
ERROR ... Servlet.service() for servlet [dispatcherServlet] ... threw exception
      [Request processing failed: java.lang.RuntimeException: query failed] with root cause
java.net.ConnectException: Connection refused
```

| Log part | Plain meaning |
|---|---|
| `Request processing failed: ... query failed` | The exception the repository threw. |
| `with root cause` | "Keep reading — the next line is the real reason." |
| `java.net.ConnectException: Connection refused` | Nothing listening at localhost:5432. Database down. |

Fix: `docker start pg`, wait a few seconds, curl again. In any Spring error dump, hunt for
the words **root cause**.

**Mistake B — believing Spring wired the chain.** It did not. You wrote three `new`s (tap,
repository, service). Spring built exactly one of your objects: the controller.

**Mistake C — typo in address, user, or password.** Same shape as Mistake A: clean
startup, failure at first request. With hand wiring, configuration mistakes hide until
first use.

### Where it is used

This exact shape — annotations on top, hand-wired `new` chains underneath — is what much
real legacy code looks like: Spring adopted for the web server, wiring kept by hand for
years.

### Link to the task

This completes subtask **S1**: the chain
`new EventService(new JdbcEventRepository(dataSource))` sits inside a working controller,
and the app answers with real data. Next lesson is **S2**: handing this controller the
fake repository, and hitting the wall.

### Do it yourself (second laptop)

**Setup checks:**

1. `java -version` → 21.
2. `docker ps` → container `pg` should be `Up`. If stopped: `docker start pg`. Verify
   data: `docker exec pg psql -U postgres -d zenith -c "SELECT count(*) FROM event;"` →
   expect `7`.
3. `mvn -v` → Apache Maven 3.9.x.

**Step 1** — folders:

```bash
mkdir -p F6.1-what-a-bean-is-and-the-application-context/scratch-app/src/main/java/com/zenith/catalog
cd F6.1-what-a-bean-is-and-the-application-context/scratch-app
```

**Step 2** — create the six files exactly as printed in "The project" section of this
lesson: `pom.xml` at the project root, the five `.java` files inside
`src/main/java/com/zenith/catalog/`.

**Step 3** — start: `mvn spring-boot:run`. Expect near the end: `Tomcat started on port
8080` and `Started CatalogApplication`. First run may download libraries.

**Step 4** — second terminal: `curl http://localhost:8080/events`. Expect the seven names.

**Step 5 (failure drill)** — `docker stop pg`, curl again: ~30 s wait, then `"status":500`.
In the app log find `root cause` → `Connection refused`. Then `docker start pg`, wait,
curl, names return.

**Done when:** the endpoint answers with the seven names, you have caused and fixed the
500, and you can count the three `new`s in the controller and name the one object of yours
that Spring built.

### Summary for revision

- Scratch app: Spring runs the web server and builds the controller; everything below is
  hand-wired in the controller's constructor — wrong on purpose.
- The chain is built once, at startup. The first database contact happens at the first
  request (the pool is lazy).
- Database down ⇒ clean startup, 500 at request time after ~30 s, `root cause: Connection
  refused` in the log.
- In Spring error dumps, always find **root cause**.
- Spring built one object of yours (the controller); your code built three (tap,
  repository, service).

### Check questions, with answers

**Question 1.** Here is a complete controller from a Spring Boot web application.
`EventService` takes an `EventRepository` (an interface) in its constructor;
`JdbcEventRepository` implements that interface:

```java
@RestController
public class EventController {

    private final EventService service;

    public EventController() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:postgresql://localhost:5432/zenith");
        dataSource.setUsername("postgres");
        dataSource.setPassword("dev");
        this.service = new EventService(new JdbcEventRepository(dataSource));
    }

    @GetMapping("/events")
    public List<String> events() {
        return service.upcomingEventNames();
    }
}
```

(a) When does this constructor run — at application startup, or when the first request
arrives? (b) How many times does it run if the app then serves one thousand requests?
(c) When does the application talk to Postgres for the first time?

**Answer.** (a) At startup. Spring sees `@RestController` and builds the controller while
the application is starting, before any request exists. Building it runs the constructor.
(b) Once. Spring builds one controller object and reuses it for every request; a thousand
requests all go through that same object. (c) At the first request. The constructor only
*configures* the connection tap. The first `dataSource.getConnection()` happens inside the
repository when the first request reaches it — that is when Postgres first hears from the
app (visible in the log: `HikariPool-1 - Starting...` appears at request time, not at
startup).

**Question 2.** Same controller as in Question 1. Four objects exist while that
application runs: the `EventController`, the `EventService`, the `JdbcEventRepository`,
and the `HikariDataSource`. For each of the four, say who created it: Spring, or your code.

**Answer.** `EventController` — Spring (because of `@RestController`). `EventService` —
your code (`new EventService(...)` in the constructor). `JdbcEventRepository` — your code
(`new JdbcEventRepository(dataSource)`). `HikariDataSource` — your code
(`new HikariDataSource()`). One out of four from Spring; the other three are the hand
wiring this task exists to remove.

---

## Lesson 5 — Feel the block (subtask S2)

### Definition

A **test** is a small program that builds your object with inputs you control, calls it,
and checks the answer. (Real projects use a test library; a plain `main` hits the same
wall.) This lesson is the attempt to test `EventController` with `FakeEventRepository` —
two hardcoded events, no database. The attempt fails, and the failure gets written down in
one sentence in `NOTES.md`.

### Why it is needed

The task says it in bold: **that blocked feeling is the entire reason dependency injection
exists.** Every annotation in the later lessons is justified by this one wall.

### The code under attack

The controller (from `scratch-app`) and the fake:

```java
@RestController
public class EventController {

    private final EventService service;

    public EventController() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:postgresql://localhost:5432/zenith");
        dataSource.setUsername("postgres");
        dataSource.setPassword("dev");
        this.service = new EventService(new JdbcEventRepository(dataSource));
    }

    @GetMapping("/events")
    public List<String> events() {
        return service.upcomingEventNames();
    }
}
```

```java
package com.zenith.catalog;

import java.util.List;

/** Keeps the EventRepository promises with two hardcoded events. Never touches a database. */
public class FakeEventRepository implements EventRepository {

    @Override
    public List<String> findAllNames() {
        return List.of("Fake Gig", "Fake Festival");
    }
}
```

### Attempt A — hand the fake in

```java
EventController controller = new EventController(new FakeEventRepository());
```

Real result from `mvn compile`:

```text
[ERROR] WiringAttempt.java:[7,38] constructor EventController in class com.zenith.catalog.EventController cannot be applied to given types;
  required: no arguments
  found:    com.zenith.catalog.FakeEventRepository
```

The Lesson 2 error, grown up: `required: no arguments` — the controller has **no door**.

### Attempt B — build it the only way it allows

File `scratch-app/src/main/java/com/zenith/catalog/WiringAttempt.java`:

```java
package com.zenith.catalog;

import java.util.List;

/** Attempt B: build the controller the only way it allows, then check the answer. */
public class WiringAttempt {

    public static void main(String[] args) {
        List<String> wanted = List.of("Fake Gig", "Fake Festival");

        EventController controller = new EventController();   // the only door there is
        List<String> got = controller.events();

        System.out.println("wanted: " + wanted);
        System.out.println("got:    " + got);
        if (got.equals(wanted)) {
            System.out.println("TEST PASSED");
        } else {
            System.out.println("TEST FAILED: the controller chose its own repository. We never got a say.");
        }
    }
}
```

Run with: `mvn spring-boot:run -Dspring-boot.run.main-class=com.zenith.catalog.WiringAttempt`

Real output (database running):

```text
00:56:55.483 [main] INFO com.zaxxer.hikari.HikariDataSource -- HikariPool-1 - Starting...
00:56:55.594 [main] INFO com.zaxxer.hikari.pool.HikariPool -- HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@33afa13b
00:56:55.595 [main] INFO com.zaxxer.hikari.HikariDataSource -- HikariPool-1 - Start completed.
wanted: [Fake Gig, Fake Festival]
got:    [Monsoon Rock Night, Jazz at the Note, Classical Evening, Open Mic Night, Sufi Night, Blues Brunch, Indie Fest]
TEST FAILED: the controller chose its own repository. We never got a say.
```

### Walkthrough — the output as evidence

- `HikariPool-1 - Starting...` — a **real connection pool** started inside a "test".
  Nobody asked; the controller's constructor did it on its own.
- `Added connection org.postgresql.jdbc.PgConnection@...` — a **real line to Postgres**
  opened. With Docker off, this "test" hangs 30 seconds and dies. A test that needs Docker
  is a hostage, not a test.
- `wanted` vs `got` — the fake was never used. In Attempt B the fake is not even
  *mentioned*: there is nowhere to put it.
- The deciding line sits inside the constructor:
  `this.service = new EventService(new JdbcEventRepository(dataSource));` — decided when
  the class was written, not when the test ran. Lesson 2's hidden `new` trap, in a real
  web app.

### Where this wall is met

Every legacy codebase has classes nobody tests; the reason is some version of "you can't
get a fake into it". Teams then skip testing (bugs ship) or start the whole app plus a
real database for every check (hour-long suites). Both trace back to this wall.

### Common mistakes

- **Blaming the interface.** The interface is fine — Lesson 3 proved the swap works when a
  door exists. The wall is only the controller building its own repository inside itself.
- **"Make the fake extend `JdbcEventRepository`."** Changes nothing: there is still no
  parameter to pass anything through, and the constructor still runs its own `new`.
- **"Test through the real database then."** That tests Docker, the network, and the data
  all at once, slowly — not the controller's logic in isolation.

### Link to the task

Subtask **S2**. It completes with **one sentence in your own words** in
`F6.1-what-a-bean-is-and-the-application-context/NOTES.md` (placeholder created). A good
sentence names the guilty line and the missing door. Write yours; do not copy anyone's
phrasing.

### Do it yourself (second laptop)

**Setup check:** the scratch app from Lesson 4 runs; `docker ps` shows `pg` as `Up`.

**Step 1** — create `FakeEventRepository.java` in
`scratch-app/src/main/java/com/zenith/catalog/` (content printed above in "The code under
attack").

**Step 2 (Attempt A)** — create `WiringAttempt.java` in the same folder:

```java
package com.zenith.catalog;

/** Attempt A: hand the controller a fake repository. */
public class WiringAttempt {

    public static void main(String[] args) {
        EventController controller = new EventController(new FakeEventRepository());
    }
}
```

Run `mvn compile` from `scratch-app`. Expect: `required: no arguments / found:
com.zenith.catalog.FakeEventRepository`. Read it out loud: no door.

**Step 3 (Attempt B)** — replace the whole content of `WiringAttempt.java` with the
Attempt B version printed above. Then:

```bash
mvn compile
mvn spring-boot:run -Dspring-boot.run.main-class=com.zenith.catalog.WiringAttempt
```

Expect: `HikariPool-1 - Starting...`, then `wanted` with the fakes, `got` with the seven
real names, then `TEST FAILED`.

**Step 4** — create `NOTES.md` in the F6.1 task folder with heading `S2 — the blocked
test` and your one sentence.

**Done when:** both failures seen with your own eyes, and `NOTES.md` holds one sentence in
your words naming what stopped you.

### Summary for revision

- The fake could not be handed in: the constructor has no parameter (`required: no
  arguments`).
- Building the controller at all starts a real pool and opens a real Postgres connection —
  inside a test.
- The deciding line lives in the constructor and ran before the test had any say.
- The interface was never the problem; the hidden `new` was.
- S2's deliverable: one honest sentence in `NOTES.md`.

### Check questions, with answers

**Question 1.** (Controller and fake as printed in "The code under attack" above —
controller with a no-argument constructor that builds
`new EventService(new JdbcEventRepository(dataSource))` inside itself; fake implementing
`EventRepository` with two hardcoded names.) A colleague suggests: "Change
`FakeEventRepository` so it `extends JdbcEventRepository` instead — then the test will
work without a database." Will that unblock the test? Explain why or why not.

**Answer.** No. The block is not the fake's type. The controller's constructor takes **no
parameters**, so nothing — whatever its parent class — can be handed in. And inside that
constructor, `new JdbcEventRepository(dataSource)` runs unconditionally, so the real
repository is built and used regardless of what else exists. Changing the fake's parent
cannot open a door that does not exist. (It would also force the fake to carry a
`DataSource`, dragging the database back into the one thing meant to avoid it.)

**Question 2.** A teammate looks at this test program and its output:

```java
public class WiringAttempt {
    public static void main(String[] args) {
        EventController controller = new EventController();
        List<String> got = controller.events();
        System.out.println(got);
    }
}
```

```text
[Monsoon Rock Night, Jazz at the Note, Classical Evening, Open Mic Night, Sufi Night, Blues Brunch, Indie Fest]
```

The teammate says: "The fake repository must be broken — that is why we got real names."
(a) Point to the exact line that decided which repository was used. (b) Was a
`FakeEventRepository` object ever created during this run?

**Answer.** (a) Inside `EventController`'s constructor:
`this.service = new EventService(new JdbcEventRepository(dataSource));`. That line alone
chose the repository. (b) No. The run never constructs a `FakeEventRepository` — the test
program does not even mention it. The fake is not broken; it was never in the game. The
output is the real `event` table, fetched through the controller's self-built chain.

---

## Lesson 6 — Dependency injection by hand

### Definition

**Dependency injection** is a style of building programs: no class ever creates its own
helpers; every class receives its helpers from outside, through its constructor.
"Injection" just means "pushed in from outside". No framework involved — plain Java.

### Why it is needed

Lesson 5 ended at a wall: the controller built its own repository, the fake could never
get in, the test printed `TEST FAILED` while a real Postgres connection opened under it.
Dependency injection is the cure for exactly that wall: same classes, different
arrangement, and the same test prints `TEST PASSED`.

### How it works, step by step

1. **Give every class a door.** Each class takes its helpers as constructor parameters.
   No class contains `new` for anything that does real work.
2. **Push every `new` up.** If a class cannot build its helpers, someone above must.
   Push the building up until it cannot go higher.
3. **Let all `new`s land in one place at the top.** In a plain program that is `main`. It
   builds everything deepest-first and hands each object to the next. Call it the
   **wiring place** (engineers also say *composition root*).

Classes below the wiring place declare what they need, use what they are given, and never
decide *which* implementation they got.

### Example

File: `scratch/lesson06/Lesson6.java`. The scratch app's shape — controller → service →
repository — in plain Java, a door on every class:

```java
import java.util.List;

interface EventRepository {
    List<String> findAllNames();
}

class JdbcEventRepository implements EventRepository {
    private final String databaseUrl;

    JdbcEventRepository(String databaseUrl) {
        this.databaseUrl = databaseUrl;
    }

    @Override
    public List<String> findAllNames() {
        // Pretend this queries the database at databaseUrl.
        return List.of("names fetched from " + databaseUrl);
    }
}

class FakeEventRepository implements EventRepository {
    @Override
    public List<String> findAllNames() {
        return List.of("Fake Gig", "Fake Festival");
    }
}

class EventService {
    private final EventRepository repository;

    EventService(EventRepository repository) {
        this.repository = repository;
    }

    List<String> upcomingEventNames() {
        return repository.findAllNames();
    }
}

class EventController {
    private final EventService service;

    EventController(EventService service) {   // the door the Spring version lacked
        this.service = service;
    }

    List<String> events() {
        return service.upcomingEventNames();
    }
}

public class Lesson6 {
    public static void main(String[] args) {
        // THE WIRING PLACE: every new in the program happens here, deepest first.
        EventRepository repository =
                new JdbcEventRepository("jdbc:postgresql://localhost:5432/zenith");
        EventService service = new EventService(repository);
        EventController controller = new EventController(service);

        System.out.println("app answers: " + controller.events());

        // The test that was impossible in the Spring scratch app, now three lines:
        EventController underTest =
                new EventController(new EventService(new FakeEventRepository()));
        List<String> got = underTest.events();
        System.out.println("test got:    " + got);
        System.out.println(got.equals(List.of("Fake Gig", "Fake Festival"))
                ? "TEST PASSED" : "TEST FAILED");
    }
}
```

Real output:

```text
app answers: [names fetched from jdbc:postgresql://localhost:5432/zenith]
test got:    [Fake Gig, Fake Festival]
TEST PASSED
```

The test Lesson 5 could not write takes three lines and passes.

### Walkthrough

- `EventController` changed in exactly one way: its constructor now says
  `EventController(EventService service)` instead of building a chain inside. A door,
  instead of a factory floor.
- `main` wires production deepest-first: repository → service → controller.
- `main` then wires a **second, parallel chain** for the test: fake → service →
  controller. Same classes, different plug; two controllers exist, each with its own
  chain.
- All five `new`s live in `main`. The four classes above contain none. That absence *is*
  dependency injection.
- The classes lost nothing: logic unchanged. They only stopped making choices that were
  never theirs.

### Important terms

| Term | Meaning |
| --- | --- |
| **Dependency injection** | Helpers are pushed in from outside; no class builds its own. |
| **Wiring place / composition root** | The one place at the top where every `new` happens, deepest first. |
| **Door** | A constructor parameter; the only way helpers enter. |

### Common mistakes

- **"We use dependency injection, so we need Spring."** Backwards. This lesson did it with
  zero libraries. Frameworks automate the wiring place; they do not invent the style.
- **Leaving one hidden `new` behind.** One class quietly building one helper re-creates
  the Lesson 5 wall below that point. Caused for real — trap restored inside
  `EventController`, recompiled:

```text
DoorGone.java:58: error: constructor EventController in class EventController cannot be applied to given types;
        EventController controller = new EventController(service);
                                     ^
  required: no arguments
  found:    EventService
DoorGone.java:64: error: constructor EventController in class EventController cannot be applied to given types;
                new EventController(new EventService(new FakeEventRepository()));
                ^
  required: no arguments
  found:    EventService
2 errors
```

  One closed door, every caller blocked; the compiler lists the blast radius.
- **Fearing `main` got "too big".** The wiring always existed; it was hidden inside
  constructors. Boring, visible wiring in one place beats invisible wiring everywhere.
  When it truly grows, that is the job Spring takes over.

### Where it is used

Every language, with or without frameworks; small tools wire in `main` forever. **What
Spring automates is the wiring place, nothing else.** The classes stay exactly like
today's; only the wirer changes — `main` today, Spring from the next lessons.

### Link to the task

The bridge lesson. Subtask **S3** ("constructor parameters, delete every `new`") is
today's transformation applied to the real scratch app, with Spring as `main`. The task's
core claim — *"Spring is, at bottom, a `Map` of objects it built for you"* — makes sense
only after being the builder yourself.

### Do it yourself (second laptop)

**Setup check:** `java -version` → 21.

**Step 1** — folder:

```bash
mkdir -p F6.1-what-a-bean-is-and-the-application-context/scratch/lesson06
cd F6.1-what-a-bean-is-and-the-application-context/scratch/lesson06
```

**Step 2** — create `Lesson6.java` with exactly the content in the Example section above.

**Step 3** — `javac Lesson6.java && java Lesson6`. Expect:

```text
app answers: [names fetched from jdbc:postgresql://localhost:5432/zenith]
test got:    [Fake Gig, Fake Festival]
TEST PASSED
```

**Step 4 (failure drill)** — restore the trap in `EventController`:

```java
EventController() {
    EventService service = new EventService(
            new JdbcEventRepository("jdbc:postgresql://localhost:5432/zenith"));
    this.service = service;
}
```

Compile. Expect **two** errors (one per wiring line in `main`), both
`required: no arguments / found: EventService`. Say out loud: one hidden `new`, every
caller blocked. Undo, compile, run, `TEST PASSED` again.

**Done when:** `TEST PASSED` prints and you can point at the wiring place and count all
five `new`s in it.

### Summary for revision

- Dependency injection = helpers pushed in through constructors; no class builds its own.
- Three moves: door on every class, push `new` up, gather all `new`s in the wiring place.
- The wiring place builds deepest-first and can wire parallel chains (production and
  test) from the same classes.
- One hidden `new` anywhere re-creates the wall; the compiler shows the blast radius.
- Spring automates the wiring place. Nothing else about the style changes.

### Check questions, with answers

**Question 1.** Here is a complete program:

```java
interface TemperatureSensor {
    int readCelsius();
}

class RoofSensor implements TemperatureSensor {
    @Override
    public int readCelsius() {
        // Pretend this reads real hardware on the roof.
        return 31;
    }
}

class FixedSensor implements TemperatureSensor {
    @Override
    public int readCelsius() {
        return -5;
    }
}

class WeatherReport {
    private final TemperatureSensor sensor;

    WeatherReport(TemperatureSensor sensor) {
        this.sensor = sensor;
    }

    String line() {
        int c = sensor.readCelsius();
        return c <= 0 ? "freezing: " + c : "mild: " + c;
    }
}

public class Q1 {
    public static void main(String[] args) {
        WeatherReport report = new WeatherReport(new FixedSensor());
        System.out.println(report.line());
    }
}
```

(a) What exactly does this program print? (b) Which method is the wiring place?
(c) To use `RoofSensor` instead, which line changes — and does `WeatherReport` change?

**Answer.** (a) `freezing: -5` — the report holds a `FixedSensor`, `readCelsius()` returns
`-5`, and `-5 <= 0` picks the "freezing" branch. (Compiled and run to confirm.) (b) `main`
— the only place any `new` happens; it builds the sensor, then the report. (c) Only the
`main` line changes: `new WeatherReport(new RoofSensor())`. `WeatherReport` itself does
not change by a single character — it asks for the interface and uses whatever arrives.

**Question 2.** A teammate says: "Our project uses dependency injection, so we must be
using Spring somewhere." Is that reasoning correct? One or two sentences.

**Answer.** No — dependency injection is a style, not a product: any plain `main` that
builds helpers and passes them through constructors is doing dependency injection with
zero libraries. Spring is one tool that *automates* that style's wiring place, so the
arrow only points the other way: using Spring implies dependency injection, not the
reverse.

---

## Lesson 7 — What a bean is

### Definition

A **bean** is an object that Spring built and is holding on to for you. That is the whole
definition. The `EventController`, once Spring has created it, is a bean.

### Why the word is needed

In the hand-wiring lesson, `main` did two jobs: it **built** the objects and it **held**
them in local variables so it could hand each one to the next constructor. When Spring
takes over `main`'s role it must do both jobs too. So one name is needed to separate two
kinds of objects in the same program:

- objects **Spring built and holds** — it can hand them out, list them, manage them;
- objects **your code built with `new`** — Spring has no idea they exist.

"Bean" names the first kind. Nothing else.

### How it works, step by step

At startup Spring behaves like the hand-written wiring `main`:

1. It finds the classes marked for it (one mark known so far: `@RestController`; the full
   family comes with the stereotype lesson).
2. It builds **one object** of each marked class, deepest-first.
3. It stores each object in its box, under a name and a type, for the whole life of the
   application.
4. Whoever asks gets **the same object** every time.

One-object-per-class is the default; its official name is **singleton** ("single
instance").

### Example — catch Spring in the act

Experiment on the scratch app. One temporary line in the controller's constructor:

```java
public EventController() {
    System.out.println(">>> EventController is being built RIGHT NOW");
    // ... rest unchanged
}
```

Start the app, send **three** requests to `/events`. Real results:

```text
3 requests served
=== how many times did the constructor line print? ===
1
=== where in the timeline did it print? ===
>>> EventController is being built RIGHT NOW
01:02:33  INFO ... TomcatWebServer  : Tomcat started on port 8080 (http)
01:02:33  INFO ... CatalogApplication : Started CatalogApplication in 0.593 seconds
01:02:39  INFO ... HikariDataSource   : HikariPool-1 - Starting...
```

### Walkthrough — the timeline as evidence

- The print sits **above** `Tomcat started on port 8080`: the controller existed before
  any request could arrive. Spring built it as part of starting up.
- Three requests, one print: one object, built once, held, reused. That object is a bean.
  No `new EventController()` exists anywhere in the code — yet exactly one instance lives.
- `HikariPool-1 - Starting...` is stamped six seconds after startup: it belongs to the
  first request. Chain built at startup; first database contact at first use.

### Important terms

| Term | Meaning |
| --- | --- |
| **Bean** | An object Spring built and is holding on to for you. |
| **Singleton** | The default: one object per class, reused for every request and every asker. |
| **JavaBean** | Unrelated 1990s term: a getter/setter class convention. Not this. |

### Common mistakes

- **"A bean is a special kind of class."** No. The class is ordinary. Bean-ness lives in
  **who built and who holds the object**. The same class can produce both: Spring's
  startup instance is a bean; the `new EventController()` from the blocked-test program
  was not — Spring never knew it existed.
- **"Every object in a Spring app is a bean."** In the wrong scratch app, four of your
  objects exist (controller, service, repository, Hikari tap) and exactly **one** is a
  bean — the controller. The other three are invisible to Spring: it cannot list them,
  hand them to anyone, or swap them. That invisibility is the blocked test, named.
- **Mixing in "JavaBean".** Same word, different world. In Spring talk, bean = object
  Spring built and holds.

### Where it is used

Everything in Spring is reached through beans: controllers, services, repositories (after
conversion), the `DataSource`, and Spring's own machinery (routing, JSON, error handling).
The bean-list lesson prints about two hundred of them in this tiny app.

### Link to the task

Learning outcome, first half: *"say in one sentence what a bean is."* The task's own
sentence is worth memorising: *"An object that Spring built and is holding on to for you.
Nothing more mysterious than that."* Subtask **S3** turns the other three objects into
beans; **S5** shows the full list.

### Do it yourself (second laptop)

**Setup check:** scratch app runs; `docker ps` shows `pg` Up.

**Step 1** — add to the top of `EventController`'s constructor:

```java
System.out.println(">>> EventController is being built RIGHT NOW");
```

**Step 2** — `mvn spring-boot:run` from `scratch-app`. Expect the `>>>` line **before**
`Tomcat started on port 8080`.

**Step 3** — second terminal, three requests:

```bash
curl -s http://localhost:8080/events > /dev/null
curl -s http://localhost:8080/events > /dev/null
curl -s http://localhost:8080/events > /dev/null
```

**Step 4** — count the `>>>` lines in the app output. Expect exactly **1**, still in the
startup section. One object, built at startup, serving every request: a bean.

**Step 5** — remove the line, stop the app (Ctrl-C).

**Done when:** single print seen above the Tomcat line, three requests added none, and
the one-sentence definition comes out without looking.

### Summary for revision

- Bean = object Spring built and is holding for you. Full stop.
- Built at startup, before any request; held for the app's life; same instance reused
  (singleton by default).
- Bean-ness is about builder + holder, not about the class. Your own `new` of the same
  class makes a non-bean.
- In the wrong app: controller = bean; service, repository, tap = invisible to Spring.
- "JavaBean" is an unrelated old term.

### Check questions, with answers

**Question 1.** A Spring Boot application contains this controller (and nothing else of
yours):

```java
@RestController
public class GreetingController {

    public GreetingController() {
        System.out.println(">>> GreetingController built");
    }

    @GetMapping("/hello")
    public String hello() {
        return "hi";
    }
}
```

The app starts, the log shows `>>> GreetingController built` exactly once, and the app
serves one thousand requests to `/hello`. (a) How many `GreetingController` objects did
Spring create in total? (b) Separately, a plain test program runs
`GreetingController c = new GreetingController();` and the print line appears in the
test's output. Is the object `c` a bean? Why or why not?

**Answer.** (a) One. Spring builds the controller once at startup (the single print
proves it) and reuses that same object for all one thousand requests — the singleton
default. (b) No. `c` is an ordinary object: *your* code built it, *your* code holds the
only reference, and Spring has no idea it exists. A bean is specifically the instance
Spring built and holds. Same class, two objects, only one bean.

**Question 2.** Here is the constructor of a controller in a running Spring Boot app:

```java
@RestController
public class EventController {

    private final EventService service;

    public EventController() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:postgresql://localhost:5432/zenith");
        dataSource.setUsername("postgres");
        dataSource.setPassword("dev");
        this.service = new EventService(new JdbcEventRepository(dataSource));
    }

    @GetMapping("/events")
    public List<String> events() {
        return service.upcomingEventNames();
    }
}
```

While this app runs, four of your objects exist: the controller, the service, the
repository, and the Hikari tap. (a) Which of the four are beans? (b) Name one concrete
thing Spring cannot do with the non-bean objects, precisely because it does not know they
exist.

**Answer.** (a) Only the controller. Spring built it because of `@RestController`. The
service, the repository, and the tap were built by `new` inside the constructor — by your
code, not by Spring. (b) Any of these: Spring cannot hand the service to another class
that needs it (each would build its own copy); it cannot show them in the bean list; it
cannot swap the repository for a fake in a test; it cannot manage or replace the tap. Any
one of the four is a correct answer — they are all the same fact: invisible objects
cannot be wired.

---

## Lesson 8 — The application context

### Definition

The **application context** is the box that holds every bean, indexed by name and by
type. It is a **real object** — holdable in a variable, with methods to call. People also
call it **the container**.

### Why it is needed

First: someone must do what the hand-wiring `main` did — build everything, hold
everything, hand each object onward. The context is that someone. Second, the task's own
warning: *"an invisible container is a magical one."* Hold the box in a variable and
interrogate it, and the magic collapses into a data structure — at bottom, a `Map` of
objects Spring built for you.

### How it works, step by step

- `SpringApplication.run(...)` **builds the context, fills it, and returns it**. Most
  programs ignore the return value — the whole reason the box feels invisible.
- Filling has two phases: collect the list of what to build (from marked classes), then
  build deepest-first, like the hand-written `main`.
- Each bean is stored under a **name** (default: class name with a small first letter,
  `EventController` → `"eventController"`) and under its **type**.
- `getBean(SomeClass.class)` (by type) or `getBean("someName")` (by name) returns **the**
  held object — never a copy.
- Asking for a type the box never built → `NoSuchBeanDefinitionException`.

### Example — interrogate the box

Temporary `main` in the scratch app (reverted afterwards):

```java
public static void main(String[] args) {
    // run() does not just start the app. It RETURNS the box.
    ApplicationContext context = SpringApplication.run(CatalogApplication.class, args);

    System.out.println(">>> the context is a real object, class: "
            + context.getClass().getSimpleName());
    System.out.println(">>> beans it is holding right now: "
            + context.getBeanDefinitionCount());

    EventController byType = context.getBean(EventController.class);
    System.out.println(">>> getBean(EventController.class) -> " + byType);

    EventController byName = (EventController) context.getBean("eventController");
    System.out.println(">>> getBean(\"eventController\")     -> " + byName);

    System.out.println(">>> same object both times? " + (byType == byName));

    System.out.println(">>> now asking the box for an EventService...");
    EventService service = context.getBean(EventService.class);
}
```

Real output:

```text
>>> the context is a real object, class: AnnotationConfigServletWebServerApplicationContext
>>> beans it is holding right now: 159
>>> getBean(EventController.class) -> com.zenith.catalog.EventController@8d8f754
>>> getBean("eventController")     -> com.zenith.catalog.EventController@8d8f754
>>> same object both times? true
>>> now asking the box for an EventService...
Exception in thread "main" org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'com.zenith.catalog.EventService' available
	at ...
	at com.zenith.catalog.CatalogApplication.main(CatalogApplication.java:28)
```

### Walkthrough — six answers from the box

- **A class name:** `AnnotationConfigServletWebServerApplicationContext` — read backwards:
  an application context, for a web-server app, configured by annotations. An object.
- **159 beans:** one written by you, 158 of Spring's machinery (router, JSON converter,
  error handler…). Where they come from is the auto-configuration story, later.
- **Same `@hash` by type and by name:** one object in memory, indexed twice. The box
  hands *the* bean, not copies.
- **`true` from `==`:** identity proven.
- **The refusal, decoded:**

| Message part | Plain meaning |
|---|---|
| `NoSuchBeanDefinitionException` | "You asked me for an object of this type and I have not got one." |
| `No qualifying bean of type '...EventService'` | "Nothing in my box is an `EventService`, and nothing I know how to build is one." |
| `at ...CatalogApplication.main(...:28)` | The exact line that asked. |

The box is right: `EventService` is built by a `new` inside the controller — invisible to
Spring. The class exists; the box does not care. **No recipe, no bean.**

### Important terms

| Term | Meaning |
| --- | --- |
| **Application context** | The box holding every bean, indexed by name and by type; a real object. |
| **Container** | The same box, by its other everyday name. |
| **`getBean`** | Ask the box by type or by name; returns the held object itself. |
| **`NoSuchBeanDefinitionException`** | "You asked me for an object of this type and I have not got one." |

### Common mistakes

- **Treating the context as abstract.** It has a class name and methods and is returned
  by `SpringApplication.run(...)`.
- **Using `getBean` as the normal way to get dependencies.** A class calling `getBean`
  hides its needs in method bodies — a polite hidden-`new` trap. Normal classes declare
  constructor parameters; `getBean` is for inspection and tests.
- **Expecting `getBean` to build on request.** It fetches what recipes produced at
  startup. No recipe — however real the class — means `NoSuchBeanDefinitionException`.

### Where it is used

Rarely injected in everyday code. Deliberate uses: debugging ("is my class in the box?"),
tests, framework-level code. Daily value: every Spring error message is the box talking
about what it holds or cannot find.

### Link to the task

Second half of the learning outcome: the context in one sentence — *the box that holds
every bean, indexed by name and by type; a real object you can inspect.* Two vocabulary
words settled: **application context**, **`NoSuchBeanDefinitionException`** (met here at
a line you wrote; returns in the package trap as a startup crash).

### Do it yourself (second laptop)

**Setup check:** scratch app runs; `pg` Up.

**Step 1** — in `CatalogApplication.java`: add import
`org.springframework.context.ApplicationContext`, replace `main`'s body with the demo
version above.

**Step 2** — `mvn spring-boot:run`. Expect after startup: context class name, ~159
beans, the controller twice with the **same** `@hash`, `true`, then the
`NoSuchBeanDefinitionException` for `EventService` pointing at your `main`.

**Step 3** — say the exception in plain words: "I asked the box for an EventService; the
box never built one, because nothing marks that class for building."

**Step 4** — restore `main` (two lines), stop the app (Ctrl-C; the web server survives
the `main` exception — separate threads — so Ctrl-C is still needed).

**Done when:** same `@hash` twice, the count, the refusal — and both one-sentence
definitions come out without looking.

### Summary for revision

- `SpringApplication.run(...)` returns the box. Store it, and the container is visible.
- Beans are indexed by name (small-first-letter class name) and by type.
- `getBean` returns the held object itself: same `@hash`, `==` true.
- `NoSuchBeanDefinitionException` = "no recipe, no bean" — the class existing on the
  classpath is irrelevant.
- Everyday code never calls `getBean`; constructors declare needs instead.

### Check questions, with answers

**Question 1.** A Spring Boot app contains exactly one marked class of yours:

```java
@RestController
public class PingController {

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
```

and one ordinary, unmarked class in the same package:

```java
public class ReportBuilder {
    public String build() {
        return "report";
    }
}
```

`main` captures the context:
`ApplicationContext context = SpringApplication.run(App.class, args);`. Predict:

(a) `context.getBean(PingController.class)` — what comes back?
(b) `context.getBean(PingController.class) == context.getBean(PingController.class)` —
`true` or `false`?
(c) `context.getBean(ReportBuilder.class)` — what happens, in plain words?

**Answer.** (a) The one `PingController` object Spring built at startup — the bean
itself, something like `PingController@1a2b3c4`. (b) `true`. Both calls return the same
held object; the box does not copy. (c) It throws
`NoSuchBeanDefinitionException: No qualifying bean of type 'ReportBuilder' available`.
Plain words: the box never built a `ReportBuilder` because nothing marks that class, so
there is no recipe and nothing to fetch. The class merely existing changes nothing.

**Question 2.** From memory, one sentence each: (a) What is a bean? (b) What is the
application context?

**Answer.** (a) A bean is an object that Spring built and is holding on to for you.
(b) The application context is the box that holds every bean, indexed by name and by
type — a real object you can hold and inspect, also called the container.

---

## Lesson 9 — Stereotype annotations

### Definition

A **stereotype annotation** is a mark on a class telling Spring: *"write a recipe for
this class, build one object of it at startup, keep it in the box."* The four everyday
members:

| Mark | What it says about the class |
|---|---|
| `@Component` | "Build me." General-purpose, no further meaning. |
| `@Service` | "Build me — and I hold business logic." |
| `@Repository` | "Build me — and I talk to the database." (One real extra, own lesson.) |
| `@Controller` / `@RestController` | "Build me — and send matching web requests to me." |

(Reminder: an **annotation** is a label starting with `@`; it does nothing alone, tools
read it and act.)

### Why it is needed

The box's rule is **no recipe, no bean** (`NoSuchBeanDefinitionException`). The
stereotype annotation is how a class gets a recipe. In hand wiring, the list of things to
build lived in `main` as typed `new`s; with Spring, that list is written as marks on the
classes themselves. Marked: recipe, bean, wire-able. Unmarked: invisible.

### How it works, step by step

1. At startup Spring walks through the classes looking for marks (the walk's exact rules
   are the component-scanning lesson).
2. Each marked class gets a recipe.
3. Then it cooks: one object per recipe, stored under type and name (default name =
   class name with small first letter: `GreetingMaker` → `"greetingMaker"`).
4. **All four marks do the identical core job.** The names exist for human readers, plus
   two real extras: `@RestController` gets requests routed; `@Repository` has one extra
   behaviour (own lesson).

### Example — one class, three runs

Temporary class in the scratch app (no dependencies), temporary questions in `main`:

```java
package com.zenith.catalog;

import org.springframework.stereotype.Component;

@Component
public class GreetingMaker {

    public String greeting() {
        return "hello from a bean";
    }
}
```

```java
GreetingMaker maker = context.getBean(GreetingMaker.class);
System.out.println(">>> found in the box: " + maker);
System.out.println(">>> it works: " + maker.greeting());
System.out.println(">>> stored under the name: "
        + java.util.Arrays.toString(context.getBeanNamesForType(GreetingMaker.class)));
```

**Run A — `@Component`:**

```text
>>> found in the box: com.zenith.catalog.GreetingMaker@d613308
>>> it works: hello from a bean
>>> stored under the name: [greetingMaker]
```

**Run B — `@Service` instead:**

```text
>>> found in the box: com.zenith.catalog.GreetingMaker@33ecbd6c
>>> it works: hello from a bean
>>> stored under the name: [greetingMaker]
```

**Run C — annotation removed entirely:**

```text
Exception in thread "main" org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'com.zenith.catalog.GreetingMaker' available
```

### Walkthrough

- **Run A:** the mark put the class in the box — built by Spring, stored as type
  `GreetingMaker` and name `greetingMaker`, handed over on request.
- **Run B:** swapping `@Component` for `@Service` changed **nothing observable**. The
  choice between marks is communication, not mechanics.
- **Run C:** no mark → the class is just a file. It compiles, it exists, the box refuses.
  This is classic reason one from the task ("no stereotype annotation"); reason two
  ("outside the scanned packages") is the package-trap lesson.

### Important terms

| Term | Meaning |
| --- | --- |
| **Stereotype annotation** | A build-me mark: `@Component`, `@Service`, `@Repository`, `@Controller`/`@RestController`. |
| **Default bean name** | Class name with a small first letter. |

### Common mistakes

- **"I marked it, so it must be a bean."** Necessary, not sufficient: a mark in a package
  Spring never walks is never seen (package-rule lesson).
- **Believing `@Service` does something `@Component` does not.** Observed: identical.
  Exceptions: `@RestController` (routing), `@Repository` (own lesson).
- **Marking a class whose constructor needs things the box lacks.** Deliberately not done
  here: `@Service` on the real `EventService` would crash at startup — its constructor
  demands an `EventRepository` and the box has none. That crash is the wiring lesson.

### Where it is used

Every Spring codebase: `@Service` on business logic, `@Repository` on database access,
`@RestController` on endpoints, `@Component` for the rest (schedulers, converters,
listeners, helpers). The mark tells a reader the class's job before any method is read.

### Link to the task

Vocabulary word **stereotype annotation**; first half of subtask **S8** (comparison
completed with `@Repository`'s extra in its own lesson). Subtask **S3** is one lesson
away — the marks are known; the wiring rule comes next.

### Do it yourself (second laptop)

**Setup check:** scratch app runs; `pg` Up.

**Step 1** — create `GreetingMaker.java` (as printed above, with `@Component`) in
`scratch-app/src/main/java/com/zenith/catalog/`.

**Step 2** — in `CatalogApplication.java`: import
`org.springframework.context.ApplicationContext`, capture the context from `run(...)`,
add the three question lines.

**Step 3** — `mvn spring-boot:run`. Expect: found / works / `[greetingMaker]`. Ctrl-C.

**Step 4** — change `@Component` to `@Service` (import
`org.springframework.stereotype.Service`). Run. Expect the same three lines. Ctrl-C.

**Step 5 (failure drill)** — delete the annotation line. Run. Expect
`NoSuchBeanDefinitionException: No qualifying bean of type '...GreetingMaker'`. Plain
words: no mark, no recipe, no bean.

**Step 6** — delete `GreetingMaker.java`, restore `main`.

**Done when:** the same class was a bean under two marks and a non-bean under none, and
all four stereotype marks and their meanings come out from memory.

### Summary for revision

- Stereotype annotation = build-me mark; four members; identical core effect.
- Default bean name: small first letter.
- The name differences are for humans (plus routing for `@RestController` and one extra
  for `@Repository`).
- No mark → no recipe → no bean → `NoSuchBeanDefinitionException`, even though the class
  compiles and exists.
- A mark alone is not sufficient: the class must also sit where Spring walks (next
  lessons).

### Check questions, with answers

**Question 1.** A Spring Boot application scans its packages normally and contains
exactly these four classes:

```java
@Service
public class PricingService {
    public int basePrice() { return 500; }
}

@Component
public class ClockHolder {
    public long now() { return System.currentTimeMillis(); }
}

public class DiscountCalculator {
    public int discounted(int price) { return price - 50; }
}

@RestController
public class PriceController {
    @GetMapping("/price")
    public int price() { return 450; }
}
```

Which classes become beans? For each class that does not, what happens on
`context.getBean(ThatClass.class)`?

**Answer.** Beans: `PricingService` (`@Service`), `ClockHolder` (`@Component`),
`PriceController` (`@RestController`) — three different marks, same effect. Not a bean:
`DiscountCalculator` — no mark, no recipe. `context.getBean(DiscountCalculator.class)`
throws `NoSuchBeanDefinitionException: No qualifying bean of type 'DiscountCalculator'
available` — in plain words, "I never built one of these, because nothing told me to."

**Question 2.** A teammate changes `@Component` to `@Service` on a class and says: "Now
Spring will treat it differently at startup — it builds services before components."
What is the honest correction?

**Answer.** The swap changes nothing mechanical: runs A and B showed identical behaviour
— same bean, same name, same availability. There is no build-order priority between
stereotypes; build order comes from dependencies (whoever is needed first is built
first). `@Service` exists to tell human readers "business logic lives here", nothing
more. The two real functional differences in the family: `@RestController` routes
requests, `@Repository` adds one behaviour covered in its own lesson.

---

## Lesson 10 — Constructor wiring

### Definition

**Wiring** is Spring handing bean A to bean B because B asked for something of A's type.
B asks through its constructor: Spring reads the parameter list and fills every slot with
a matching bean from the box. This is **constructor injection**.

### Why it is needed

In hand wiring, `main` knew what to pass because you typed it. Spring types nothing — it
reads the only honest document there is: **the constructor is the shopping list.**
`EventService(EventRepository repository)` says "to build me, bring one EventRepository";
Spring takes that literally and shops in the box.

### How it works, step by step

When Spring is about to build a bean:

1. Read the constructor parameters, left to right.
2. For each parameter **type**, search recipes and box for a matching bean.
3. Match not built yet → build it **first**. Deepest-first order emerges automatically.
4. Call the constructor with the found beans. Every asker shares **the same instance**.
5. No match anywhere → the application refuses to start, with a readable message.

Details: a parameter typed by an **interface** matches a bean whose class implements it
(plug and socket). A class with exactly **one** constructor needs no extra annotation;
`@Autowired` on constructors is only required to pick between several.

### Example — watch the hand-over

Two temporary classes in the scratch app:

```java
@Component
public class WordSupplier {

    public String word() {
        return "zenith";
    }
}
```

```java
@Component
public class ShoutMaker {

    private final WordSupplier supplier;

    public ShoutMaker(WordSupplier supplier) {   // the shopping list: one WordSupplier, please
        System.out.println(">>> Spring just handed me: " + supplier);
        this.supplier = supplier;
    }

    public String shout() {
        return supplier.word().toUpperCase() + "!";
    }
}
```

Real output (temporary `main` fetched the `ShoutMaker` and printed the supplier bean):

```text
>>> Spring just handed me: com.zenith.catalog.WordSupplier@16a49a5d
>>> shout(): ZENITH!
>>> the supplier handed into ShoutMaker is the bean itself? true
>>> supplier bean in the box: com.zenith.catalog.WordSupplier@16a49a5d
```

### Walkthrough

- The hand-over line printed **during startup**, from inside `ShoutMaker`'s constructor.
  No `new WordSupplier()`, no `new ShoutMaker(...)` anywhere — Spring read the list,
  built the supplier first, called the constructor. The task's promised sight: *"an
  object you never called `new` on being handed to your constructor fully built."*
- The two hashes match (`@16a49a5d` twice): the object pushed through the constructor
  door **is** the bean in the box. One shared instance for all askers.

### The failure every beginner must learn to read

`@Component` removed from `WordSupplier` only. The app refuses to start. Real output:

```text
***************************
APPLICATION FAILED TO START
***************************

Description:

Parameter 0 of constructor in com.zenith.catalog.ShoutMaker required a bean of type 'com.zenith.catalog.WordSupplier' that could not be found.

Action:

Consider defining a bean of type 'com.zenith.catalog.WordSupplier' in your configuration.
```

| Message part | Plain meaning |
|---|---|
| `APPLICATION FAILED TO START` | Startup stopped; nothing is listening. Not a request error. |
| `Parameter 0 of constructor in ...ShoutMaker` | "The first slot of ShoutMaker's shopping list…" |
| `required a bean of type '...WordSupplier'` | "…asked for this exact type…" |
| `that could not be found` | "…and my box has no such bean and no recipe for one." |
| `Action: Consider defining a bean...` | Repair hint: give that type a recipe (add the mark, or fix its location). |

Read as one sentence: *who* wanted, *what* was wanted, *fix direction*. It is the
startup-crash cousin of `NoSuchBeanDefinitionException`: same disease (no recipe), found
during wiring instead of at a `getBean` call.

### Important terms

| Term | Meaning |
| --- | --- |
| **Wiring** | Spring handing bean A to bean B because B asked for A's type. |
| **Constructor injection** | The asking happens via constructor parameters. |
| **Shopping list** | The constructor parameter list — Spring fills it from the box. |
| **`@Autowired`** | Only needed to pick between multiple constructors; one constructor needs nothing. |

### Common mistakes

- **Plain values in the constructor.** `ShoutMaker(String prefix)` crashes the same way:
  no bean of type `String`. Constructor slots are for beans; configuration values travel
  by other routes (seen in the conversion lesson).
- **Two constructors, no guidance.** Spring fails asking for `@Autowired` on one. Keep
  one constructor per class.
- **"Fixing" the crash with `new`.** `this.supplier = new WordSupplier();` compiles,
  starts, and silently reinstalls the hidden-`new` trap. The correct fix is on the
  missing bean's side: give it a recipe.

### Where it is used

Every modern Spring class is wired this way: one constructor, final fields, beans in.
Older code wires fields directly with `@Autowired` on them; constructor injection is
preferred — honest contracts, `final` fields, and classes testable without Spring (hand
wiring works on them, as the next lesson's test proves).

### Link to the task

Settles the vocabulary word **wiring** and delivers the "when it works you will see"
moment. With stereotypes (previous lesson) plus wiring (this one), subtask **S3** is
fully unlocked.

### Do it yourself (second laptop)

**Setup check:** scratch app runs; `pg` Up.

**Step 1** — create `WordSupplier.java` and `ShoutMaker.java` as printed above.

**Step 2** — in `CatalogApplication.java`, capture the context and add:

```java
ShoutMaker shoutMaker = context.getBean(ShoutMaker.class);
System.out.println(">>> shout(): " + shoutMaker.shout());
System.out.println(">>> supplier bean in the box: " + context.getBean(WordSupplier.class));
```

**Step 3** — `mvn spring-boot:run`. Expect: hand-over line with a hash, `ZENITH!`, box
line with the **same hash**. Ctrl-C.

**Step 4 (failure drill)** — remove `@Component` (and import) from `WordSupplier` only.
Run. Expect the full `APPLICATION FAILED TO START` block; read Description and Action out
loud. Restore, run, `ZENITH!` again. Ctrl-C.

**Step 5** — delete both demo classes, restore `main`.

**Done when:** same hash seen in hand-over and box, the crash caused and read, and the
rule stated from memory: the constructor is the shopping list; Spring fills it from the
box.

### Summary for revision

- Wiring = Spring filling constructor slots with beans, matched by type (interfaces
  count).
- Build order emerges deepest-first because helpers are built before their askers.
- All askers share the same bean instance (same `@hash`).
- Missing type → `APPLICATION FAILED TO START` with Description (who, what) and Action
  (fix direction). Always read both.
- One constructor per class; never "fix" wiring crashes with `new`.

### Check questions, with answers

**Question 1.** A Spring Boot application (normal scanning) contains exactly:

```java
@Component
public class Engine {
    public Engine() {
        System.out.println("engine built");
    }
}

@Component
public class Car {
    private final Engine engine;

    public Car(Engine engine) {
        System.out.println("car built");
        this.engine = engine;
    }
}

@Component
public class Garage {
    private final Car car;

    public Garage(Car car) {
        System.out.println("garage built");
        this.car = car;
    }
}
```

(a) In what order do the three "built" lines appear, and why is that order forced?
(b) Adding `@Component class Mechanic { Mechanic(Car car) {...} }` — how many `Car`
objects exist, one or two?

**Answer.** (a) `engine built`, then `car built`, then `garage built`. Forced because
each constructor cannot run until its parameter exists: `Car` needs an `Engine` object
first, `Garage` needs a `Car` object first. Deepest-first emerges from the shopping
lists. (b) One. Beans are shared singletons: `Garage` and `Mechanic` both receive the
same `Car` instance from the box, not private copies.

**Question 2.** An application contains these two classes and nothing else of yours:

```java
public class PriceCatalog {
    public int priceOf(String item) { return 500; }
}

@Service
public class CheckoutService {
    private final PriceCatalog catalog;

    public CheckoutService(PriceCatalog catalog) {
        this.catalog = catalog;
    }
}
```

(a) Does this application start? (b) If not, state the Description and Action parts of
the failure in your own words.

**Answer.** (a) No. `CheckoutService` is marked, so Spring tries to build it and must
fill the `PriceCatalog` slot — but `PriceCatalog` has no mark, no recipe, no bean.
Startup fails. (b) Description: "Parameter 0 of constructor in `CheckoutService` required
a bean of type `PriceCatalog` that could not be found." Action: "Consider defining a bean
of type `PriceCatalog` in your configuration" — i.e. give it a recipe, here by adding a
stereotype mark such as `@Component` to `PriceCatalog`.

---

## Lesson 11 — Convert the scratch project (subtask S3)

### Definition

The conversion: the same four classes, with Spring as the wiring place. Marks go on
(`@Service`, `@Repository`), constructors are the doors, and **every `new` that wired
your classes together is deleted**. The app answers exactly as before.

### Why it is needed

The wrong version worked but was frozen: untestable, unswappable, configuration buried in
a constructor. The converted version behaves identically — same URL, same seven events —
but every object is a bean: listable, wireable, swappable.

### The six edits

**Edit 1 — the controller loses its factory floor:**

```java
// BEFORE: no door, three hidden news, configuration in code
public EventController() {
    HikariDataSource dataSource = new HikariDataSource();
    dataSource.setJdbcUrl("jdbc:postgresql://localhost:5432/zenith");
    dataSource.setUsername("postgres");
    dataSource.setPassword("dev");
    this.service = new EventService(new JdbcEventRepository(dataSource));
}

// AFTER: a door; Spring fills it
public EventController(EventService service) {
    this.service = service;
}
```

**Edit 2 — `@Service` on `EventService`** (import
`org.springframework.stereotype.Service`). The class was already door-shaped; one mark
added.

**Edit 3 — `@Repository` on `JdbcEventRepository`** (import
`org.springframework.stereotype.Repository`). Its constructor already asked for a
`DataSource`.

**Edit 4 — who supplies the `DataSource`?** In `pom.xml`, the hand-used `HikariCP`
dependency is replaced by:

```xml
<!-- JDBC support: brings the Hikari pool AND lets Spring build the DataSource bean. -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
```

With this starter present, Spring Boot writes a recipe for a Hikari `DataSource` bean
itself (the mechanism is auto-configuration — Task F6.9's story).

**Edit 5 — the address moves out of the code** into
`src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/zenith
    username: postgres
    password: dev
```

**Edit 6 — the fake stays unmarked.** `FakeEventRepository` keeps no annotation (see the
failure drill).

The updated test, `WiringAttempt.java`:

```java
package com.zenith.catalog;

import java.util.List;

/** The test that was impossible before the conversion. Now it is three lines. */
public class WiringAttempt {

    public static void main(String[] args) {
        List<String> wanted = List.of("Fake Gig", "Fake Festival");

        EventController controller =
                new EventController(new EventService(new FakeEventRepository()));
        List<String> got = controller.events();

        System.out.println("wanted: " + wanted);
        System.out.println("got:    " + got);
        System.out.println(got.equals(wanted) ? "TEST PASSED" : "TEST FAILED");
    }
}
```

### Proof it works (all real)

**Proof 1 — the `new` census.** `grep -rn "new " src/main/java/`:

```text
WiringAttempt.java:12:   new EventController(new EventService(new FakeEventRepository()));
JdbcEventRepository.java:23:   List<String> names = new ArrayList<>();
JdbcEventRepository.java:33:   throw new RuntimeException("query failed", e);
```

Three survivors, all legitimate: a test's deliberate hand-wiring, a plain data holder,
an error object. **Zero `new`s wire the classes together.**

**Proof 2 — the app answers as before:**

```text
$ curl http://localhost:8080/events
["Monsoon Rock Night","Jazz at the Note","Classical Evening","Open Mic Night","Sufi Night","Blues Brunch","Indie Fest"]
HTTP 200
```

**Proof 3 — the Lesson 5 wall is gone:**

```text
wanted: [Fake Gig, Fake Festival]
got:    [Fake Gig, Fake Festival]
TEST PASSED
```

Hikari lines in that test run: **0**. No pool, no connection, no Docker.

### Walkthrough — startup after conversion

1. Scanning finds the three marked classes; Boot adds its `DataSource` recipe.
2. Shopping lists force the order: `DataSource` (reads `application.yml`) →
   `JdbcEventRepository` (gets the `DataSource` bean) → `EventService` (asks for the
   **interface** `EventRepository`; `jdbcEventRepository` implements it — plug fits
   socket) → `EventController` (gets the service).
3. Four beans in the box, chain assembled, Tomcat listens. The old hand wiring happens
   again — performed by Spring, same order.

### Failure drill — when two plugs fit the socket

`@Repository` added to `FakeEventRepository`, nothing else changed. Real result:

```text
***************************
APPLICATION FAILED TO START
***************************

Description:

Parameter 0 of constructor in com.zenith.catalog.EventService required a single bean, but 2 were found:
	- fakeEventRepository: defined in file [...FakeEventRepository.class]
	- jdbcEventRepository: defined in file [...JdbcEventRepository.class]

Action:

Consider marking one of the beans as @Primary, updating the consumer to accept multiple beans, or using @Qualifier to identify the bean that should be consumed
```

| Message part | Plain meaning |
|---|---|
| `required a single bean, but 2 were found` | "The slot asks for one `EventRepository`; two beans match; I refuse to guess." |
| the two-name list | Exactly which beans collided, and where each came from. |
| `Action: ... @Primary ... @Qualifier ...` | Tie-breakers exist; the right fix here is simpler — unmark the fake. |

The two crashes side by side: zero matches → `required a bean ... that could not be
found`; two matches → `required a single bean, but 2 were found`. Mark removed,
re-verified: seven names again.

### Common mistakes

- **Marking test fakes.** Fakes stay unmarked; tests wire by hand.
- **Leaving one `new` behind.** Run the census after converting.
- **Thinking the Hikari knowledge vanished.** It moved to `application.yml`. App starts
  but first request fails → suspect a typo there; `root cause` will say so.

### Link to the task

Subtask **S3** complete and verified three ways. Done-when line one satisfied: no `new`
wiring your own classes. Remaining from done-when: bean list printed (S4/S5), package
failure caused and fixed (S6).

### Do it yourself (second laptop)

**Setup check:** the wrong scratch app runs; `pg` Up.

1. `pom.xml`: replace the `HikariCP` dependency with `spring-boot-starter-jdbc` (Edit 4).
2. Create `src/main/resources/application.yml` (Edit 5).
3. `@Service` on `EventService`; `@Repository` on `JdbcEventRepository`; fake stays
   unmarked.
4. Rewrite `EventController` to the two-line constructor; remove the Hikari import.
5. Replace `WiringAttempt.java` with the three-line passing test (full file above).
6. Verify the three proofs: `grep -rn "new " src/main/java/` · `mvn spring-boot:run` +
   `curl http://localhost:8080/events` · `mvn spring-boot:run
   -Dspring-boot.run.main-class=com.zenith.catalog.WiringAttempt`.
7. **Failure drill:** `@Repository` on the fake → read the `required a single bean, but
   2 were found` block including Action → remove it → verify again.

**Done when:** all three proofs pass and the two-beans crash was caused and fixed.

### Summary for revision

- Conversion = marks on, doors everywhere, zero wiring `new`s, config in
  `application.yml`, `DataSource` bean from the starter.
- Behaviour identical; properties opposite: everything listable, wireable, swappable.
- Interface slots are filled by implementing beans — unless zero or two match: both
  failures have named messages. Read Description and Action.
- Fakes never get marks; tests are their own wiring place.

### Check questions, with answers

**Question 1.** After the conversion, a teammate says: "Spring made our code untestable —
I cannot construct `EventController` without starting Spring." The converted controller:

```java
@RestController
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @GetMapping("/events")
    public List<String> events() {
        return service.upcomingEventNames();
    }
}
```

Right or wrong? If wrong, write the single plain-Java line that builds it with a fake
(`EventService(EventRepository)` and `FakeEventRepository implements EventRepository`
exist), no Spring running.

**Answer.** Wrong — the opposite happened. The annotations are inert labels unless Spring
is running; the class is plain Java with an honest constructor. One line:

```java
EventController controller = new EventController(new EventService(new FakeEventRepository()));
```

That line is exactly the verified `TEST PASSED` run. The conversion *created*
testability: the wrong version's constructor had no door at all.

**Question 2.** In the converted app, `EventService` asks for the interface
`EventRepository`; exactly one bean implements it (`jdbcEventRepository`). (a) A second
implementing class `CachedEventRepository` is added **with** `@Repository` — what happens
at startup? (b) The same class is added **without** any annotation — what happens?

**Answer.** (a) Startup fails with `Parameter 0 of constructor in ...EventService
required a single bean, but 2 were found`, listing `cachedEventRepository` and
`jdbcEventRepository`. Two plugs fit the socket; Spring refuses to guess (tie-breakers:
`@Primary`, `@Qualifier`). (b) Nothing changes. Unmarked means no recipe, no bean,
invisible: the app starts and runs on `jdbcEventRepository` exactly as before. A class
merely existing on disk never enters the box.

---

## Lesson 12 — Bean definition vs bean instance (subtask S9)

### Definition

A **bean definition** is the recipe: which class, which constructor, what to pass in,
how many to make. A **bean instance** is the cooked dish: the actual object in memory.
The task's table: *"the recipe rather than the object. Spring reads all the recipes
first, then builds the objects."*

### Why it is needed

Startup is **two phases**, and errors carry the phase in their wording.
`NoSuchBean**Definition**Exception` does not say "object missing"; it says "I do not even
have a *recipe* for that type." Recipe and dish separated makes Spring's messages precise.

### How it works, step by step

**Phase 1 — collect recipes (nothing built).** Scanning finds marked classes; one bean
definition per class. Boot adds its own recipes (the `DataSource` recipe after the
conversion). End of phase: a shelf of cards, zero objects.

**Phase 2 — cook.** Walk the recipes, build instances deepest-first by constructor
shopping lists, store in the box.

Turning point: normal singletons are cooked **during startup, eagerly** — all before
`Started CatalogApplication` prints. That is why wiring mistakes crash startup, not the
first request. Exception: a bean marked `@Lazy` keeps its recipe on the shelf and is
cooked only when first asked for.

### Example — a recipe in one hand, the dish in the other

Temporary extra class, deliberately lazy:

```java
@Lazy
@Component
public class SleepyBean {

    public SleepyBean() {
        System.out.println("!!! SleepyBean constructor running NOW");
    }
}
```

Temporary `main`:

```java
ConfigurableApplicationContext context = SpringApplication.run(CatalogApplication.class, args);

BeanDefinition recipe = context.getBeanFactory().getBeanDefinition("eventService");
System.out.println(">>> RECIPE for eventService -> class to build: " + recipe.getBeanClassName());
System.out.println(">>> RECIPE scope            -> " + recipe.getScope());
System.out.println(">>> INSTANCE cooked from it -> " + context.getBean("eventService"));

System.out.println(">>> recipe for sleepyBean exists? "
        + context.getBeanFactory().containsBeanDefinition("sleepyBean"));
System.out.println(">>> but notice: no '!!!' line has printed so far.");
System.out.println(">>> now we ask the box for the instance...");
System.out.println(">>> got: " + context.getBean(SleepyBean.class));
```

Real output:

```text
>>> RECIPE for eventService -> class to build: com.zenith.catalog.EventService
>>> RECIPE scope            -> singleton
>>> INSTANCE cooked from it -> com.zenith.catalog.EventService@2668c286
>>> recipe for sleepyBean exists? true
>>> but notice: no '!!!' line has printed so far.
>>> now we ask the box for the instance...
!!! SleepyBean constructor running NOW
>>> got: com.zenith.catalog.SleepyBean@7f353a0f
```

### Walkthrough

- The recipe printed a **string** (`class to build: com.zenith.catalog.EventService`)
  and a plan (`scope: singleton` = cook one, share it). A card, not a dish.
- The instance printed `EventService@2668c286` — an object with identity. Different
  methods, different worlds: `getBeanDefinition(...)` reads the shelf; `getBean(...)`
  reaches into the box.
- `recipe for sleepyBean exists? true` with no `!!!` anywhere in startup: **a definition
  existed while no instance did.**
- The cooking moment, caught live: ask → `!!!` fires → dish appears. For non-lazy beans
  this moment happens during startup, once per recipe, before `Started ...`.

### Important terms

| Term | Meaning |
| --- | --- |
| **Bean definition** | The recipe card: class name, construction plan, scope. |
| **Bean instance** | The cooked object in the box. |
| **Eager (default)** | Singletons are cooked during startup, before the port opens. |
| **`@Lazy`** | Opt-out: keep the card on the shelf; cook at first request. |

### Common mistakes

- **"Spring builds beans when requests arrive."** Singletons are cooked at startup;
  requests meet ready-made objects. `@Lazy` is the opt-in exception.
- **"Definition count = built objects."** `getBeanDefinitionCount()` counts cards; the
  sleepy bean was counted while uncooked.
- **Reading `NoSuchBeanDefinitionException` as a lost object.** The recipe never
  existed. Fix at recipe level: add the mark, or fix the location (scanning lesson).

### Where it is used

In error placement: `NoSuchBeanDefinitionException` → phase 1 (no recipe).
`APPLICATION FAILED TO START ... could not be found` → phase 2 (cooking stopped, missing
ingredient recipe). `required a single bean, but 2 were found` → phase 2 (two recipes
produced matching dishes). All three caused by hand in earlier lessons; now placeable on
the startup timeline.

### Link to the task

Subtask **S9** answered hands-on. The AI prompt to run and compare: *"Explain what a
Spring bean definition is, how it differs from the bean instance itself, and at what
point during startup Spring turns one into the other."* Checkable against captured
evidence: card = class-name string; dish = `@hash` object; turning point = startup
cooking (normal) or first ask (`@Lazy`).

### Do it yourself (second laptop)

**Setup check:** converted scratch app runs; `pg` Up.

1. Create `SleepyBean.java` as printed (imports:
   `org.springframework.context.annotation.Lazy`,
   `org.springframework.stereotype.Component`).
2. In `CatalogApplication.java`: capture
   `ConfigurableApplicationContext context = SpringApplication.run(...)` (imports:
   `org.springframework.context.ConfigurableApplicationContext`,
   `org.springframework.beans.factory.config.BeanDefinition`), add the seven demo lines.
3. `mvn spring-boot:run`. Read each line out loud: card (string) → dish (`@hash`) →
   card exists for sleepy → no `!!!` yet → ask → `!!!` → dish. Ctrl-C.
4. Delete `SleepyBean.java`, restore `main`.
5. Ask an AI the exact S9 prompt; compare its answer with your output. Where they
   disagree, trust the output.

**Done when:** a definition was seen existing without its instance, the cooking moment
was caught live, and recipe-vs-dish can be explained without notes.

### Summary for revision

- Definition = recipe card (class name, plan, scope). Instance = cooked object.
- Startup: phase 1 collects all cards; phase 2 cooks all singleton dishes eagerly.
- `@Lazy` postpones cooking to the first ask — proof that card ≠ dish.
- `NoSuchBeanDefinitionException` is a phase-1 complaint: no card at all.
- Shelf (`getBeanDefinition`) and box (`getBean`) are different places with different
  methods.

### Check questions, with answers

**Question 1.** A Spring Boot application (normal scanning) contains exactly:

```java
@Component
public class Alpha {
    public Alpha() {
        System.out.println("alpha cooked");
    }
}

@Lazy
@Component
public class Beta {
    public Beta() {
        System.out.println("beta cooked");
    }
}
```

`main`:

```java
public static void main(String[] args) {
    ApplicationContext context = SpringApplication.run(App.class, args);
    System.out.println("startup finished");
    context.getBean(Beta.class);
    System.out.println("done");
}
```

Write the four print lines in order, and say in one sentence why `beta cooked` sits
where it sits.

**Answer.**

```text
alpha cooked
startup finished
beta cooked
done
```

`alpha cooked` prints during `run(...)` — eager singletons are cooked in startup's
phase 2. `beta cooked` prints **after** `startup finished` because `@Lazy` kept Beta as
a recipe card until the `getBean(Beta.class)` call forced the cooking.

**Question 2.** A colleague gets
`NoSuchBeanDefinitionException: No qualifying bean of type 'ReportService' available`
and says: "The object must have been garbage-collected. How do I stop Spring from
deleting my beans?" Using recipe-versus-dish: what is the actual situation, and in which
startup phase does the real problem live?

**Answer.** Nothing was deleted, because nothing was ever built — and nothing was ever
built because there was never a **recipe**. The exception's own name says "definition":
phase 1 never produced a card for `ReportService`. The real problem is at recipe level:
the class has no stereotype mark, or it sits outside the scanned packages. Beans in the
box are never garbage-collected while the app runs — the box itself holds them. Fix the
card, and the dish follows.

---

## Lesson 13 — Component scanning and the downward rule (subtask S7)

### Definition

**Component scanning** is Spring walking through the packages at startup, looking for
stereotype-marked classes and writing a recipe card for each one found. The governing
rule: **scanning starts at the package containing the `@SpringBootApplication` class and
goes downwards only.** A class to the side is never looked at, never becomes a bean, and
nothing can be wired with it.

### Why it is needed

The two classic reasons a marked class is not a bean: *no stereotype annotation* (caused
in the stereotype lesson), or *outside the scanned packages* — this lesson. The second is
sneakier: the mark is present and correct, the class compiles, and nothing warns.
Location silently decides whether the mark is ever read.

### How it works, step by step

1. `CatalogApplication` carries `@SpringBootApplication` and lives in
   `com.zenith.catalog` → that package becomes the **scanning root**.
2. The walk covers the root and every package **beneath** it (`com.zenith.catalog.*`,
   any depth).
3. Every mark found → one recipe card (phase 1 of startup).
4. Packages **beside** (`com.zenith.other`) or **above** (`com.zenith`) the root are
   never entered. No card → nothing to cook in phase 2.

Tree picture: Spring climbs down branches from the root; never sideways to a sibling
trunk, never up.

### Example — the same class, two addresses

The probe (announces itself when built) plus one temporary question in `main`:

```java
package com.zenith.catalog.probes;

import org.springframework.stereotype.Component;

@Component
public class ProbeBean {

    public ProbeBean() {
        System.out.println(">>> ProbeBean built (package: " + getClass().getPackageName() + ")");
    }
}
```

```java
System.out.println(">>> box contains 'probeBean'? " + context.containsBean("probeBean"));
```

**Run A — probe in `com.zenith.catalog.probes` (below the root):**

```text
>>> ProbeBean built (package: com.zenith.catalog.probes)
>>> box contains 'probeBean'? true
```

**Run B — identical file moved sideways to `com.zenith.other`:**

```text
ProbeBean lines in the log: 0
>>> box contains 'probeBean'? false
Started CatalogApplication lines: 1
```

### Walkthrough — run B as evidence

- Zero build lines: the constructor never ran; the walk never entered the package.
- `false` from the box: not an error, just absence. The mark sits valid and useless,
  like a sign in a room nobody enters.
- **The app started anyway.** With no dependents, a sideways move is a **silent**
  failure: no crash, no warning, one bean missing.
- The compiler was happy in both runs. Nothing checks packages against the scanning
  root — except Spring, at runtime, by simply not finding things.
- The loud version happens when the vanished class is on somebody's shopping list:
  `EventService` moved sideways → its card vanishes the same way → `EventController`'s
  constructor slot cannot be filled → `APPLICATION FAILED TO START ... required a bean
  of type 'EventService' that could not be found`. That deliberate break is the package
  trap (S6).

### Important terms

| Term | Meaning |
| --- | --- |
| **Component scanning** | The startup walk that reads marks and writes recipe cards. |
| **Scanning root** | The package of the `@SpringBootApplication` class. |
| **Downward rule** | Root and below only; never sideways, never up. |
| **Silent vs loud failure** | No dependents → quiet absence; a dependent's shopping list → startup crash. |

### Common mistakes

- **The shared sibling.** `com.zenith.common` next to the root: every mark there is
  silently dead. Move it under the root (or, rarely, widen the scan with
  `@ComponentScan(basePackages = ...)`).
- **The buried application class.** Moving the main class *down* into
  `com.zenith.catalog.app` makes the root deeper, leaving former siblings above it
  unscanned. The main class belongs at the top of the code.
- **Blaming the mark.** Stacking annotations will not help a class at the wrong address.
  Missing from the box → check the package against the root *before* touching the mark.

### Where it is used

This rule is the standard Boot layout, explained: application class at the top package,
everything beneath it. Not taste — it guarantees the walk covers all code.

### Link to the task

Subtask **S7**, verbatim, witnessed: `true` below the root, `false` beside it, healthy
startup both times. S6 (the loud break with `EventService`) follows with its message
already predicted.

### Do it yourself (second laptop)

**Setup check:** converted scratch app runs; `pg` Up.

1. Create `scratch-app/src/main/java/com/zenith/catalog/probes/ProbeBean.java` as
   printed above.
2. In `CatalogApplication.java`: capture the context, add the `containsBean` print.
3. **Run A:** `mvn spring-boot:run`. Expect the build line and `true`. Ctrl-C.
4. **Run B:** move the file to `scratch-app/src/main/java/com/zenith/other/`, change its
   package line to `com.zenith.other`, delete the empty `probes` folder. Run. Expect no
   build line, `false`, and a normal `Started CatalogApplication`. Say out loud: the
   mark is still there; Spring never read it.
5. Delete the probe and the `com/zenith/other` folder; restore `main`.

**Done when:** `true` below, `false` beside, healthy both times, and the rule recited
from memory.

### Summary for revision

- Scanning root = the `@SpringBootApplication` package. Walk goes down only.
- Sideways and above are never entered; marks there are never read.
- No dependents → silent absence. A dependent → startup crash naming the missing type.
- Compilation says nothing about scanning; a class can compile perfectly and be
  invisible.
- Main class at the top package is the layout that makes everything work.

### Check questions, with answers

**Question 1.** The main class:

```java
package com.shop.store;

@SpringBootApplication
public class StoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(StoreApplication.class, args);
    }
}
```

Four classes, each with a valid `@Component` mark:

1. `com.shop.store.PricingEngine`
2. `com.shop.store.checkout.CartService`
3. `com.shop.billing.InvoiceMaker`
4. `com.shop.TaxTable`

Which become beans, and why?

**Answer.** Root = `com.shop.store`. (1) Bean — in the root itself. (2) Bean —
`checkout` is beneath the root. (3) Not a bean — `com.shop.billing` is a sibling; the
walk never enters it. (4) Not a bean — `com.shop` is *above* the root; the walk never
goes up. Marks 3 and 4 are valid and never read.

**Question 2.** Moving `ProbeBean` sideways did not crash the app, yet moving
`EventService` sideways will produce `APPLICATION FAILED TO START`. Both are properly
marked; both vanish identically. Where does the difference come from?
(`EventController(EventService service)` is the controller's constructor; nothing asks
for a `ProbeBean`.)

**Answer.** The difference is **dependents**, not the vanishing. Vanishing is identical:
no card is written for either class. `ProbeBean` is on nobody's shopping list, so its
absence changes nothing visible — a silent failure. `EventService` is on
`EventController`'s shopping list; during cooking, Spring cannot fill
`Parameter 0 of constructor in EventController`, so startup stops loudly, naming the
exact type it wanted and could not find. Same disease, different symptom: quiet when
unneeded, fatal when needed.

---

## Lesson 14 — Print every bean (subtasks S4 and S5)

### Definition

`BeanLister` is a bean whose only job is to print the name of every recipe in the box,
once, right after startup. The task's own code, added to the scratch app as
`BeanLister.java`:

```java
package com.zenith.catalog;

import java.util.Arrays;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
class BeanLister implements CommandLineRunner {
    private final ApplicationContext context;

    BeanLister(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(String... args) {
        Arrays.stream(context.getBeanDefinitionNames())
              .sorted()
              .forEach(System.out::println);
    }
}
```

### Why it is needed

The task: *"Make the context visible, because an invisible container is a magical one."*
After this, "is my class a bean?" is a line in a list, present or absent — never a guess.

### How it works — three new pieces

- **`CommandLineRunner`:** a Spring interface with one promise, `run(String...)`. The
  deal: any **bean** implementing it gets `run` called once, right after startup
  completes. Fine print: only beans get the call — hence `@Component`.
- **The box injects itself:** the constructor asks for `ApplicationContext`, and Spring
  hands it one. The context is itself available for wiring. No special case.
- **`getBeanDefinitionNames()`:** every recipe name as a `String[]`; then F3.3 tools —
  stream, sort, print each with the method reference `System.out::println`.

### Real output — the inventory

```text
TOTAL BEAN NAMES PRINTED: 201
```

First ten, alphabetical:

```text
applicationAvailability
applicationTaskExecutor
applicationTaskExecutorAsyncConfigurer
basicErrorController
beanLister
beanNameHandlerMapping
beanNameViewResolver
bootstrapExecutorAliasPostProcessor
catalogApplication
characterEncodingFilter
```

The lines that matter, with positions:

```text
  5: beanLister
 14: dataSource
 24: eventController
 25: eventService
 40: jdbcEventRepository
```

Strangers worth knowing:

```text
 18: dispatcherServlet
 21: errorAttributes
 35: jacksonObjectMapper
187: tomcatServletWebServerFactory
```

### Walkthrough — reading the list properly (S5)

- **Your three:** `eventController`, `eventService`, `jdbcEventRepository` — lowercase
  because these are *bean names*, not class names. The whole conversion, visible as
  three inventory lines.
- **`dataSource`:** the bean you never wrote — Boot's recipe, triggered by
  `spring-boot-starter-jdbc` from the conversion.
- **`beanLister` lists itself:** only beans get the `CommandLineRunner` call, so the
  census-taker appears in its own census.
- **`catalogApplication`:** the `@SpringBootApplication` class is itself a bean.
- **The ~196 strangers:** `dispatcherServlet` routes every request to your methods;
  `jacksonObjectMapper` made your JSON array; `errorAttributes` built the
  `{"status":500,...}` body; `tomcatServletWebServerFactory` makes the web server. They
  come from auto-configuration — Task F6.9's story. For now: **the container is a list
  of objects, and you can see all of them.**
- The method reads the **shelf** (`getBeanDefinitionNames`), so the count (201) matches
  the recipe cards.

### Important terms

| Term | Meaning |
| --- | --- |
| **`CommandLineRunner`** | Bean interface; `run` is called once, right after startup completes. |
| **Self-injection of the context** | The box is available as a bean; constructors can ask for it. |
| **Bean name vs class name** | The list shows `eventService`, not `EventService`. |

### Common mistakes

- **Grepping for `EventService` and declaring it missing.** The list holds bean names,
  small first letter.
- **Trying to study all 201.** The value is not knowing every entry; it is that nothing
  is hidden. Magic shrinks to inventory.
- **Forgetting the lister must be a bean.** Without `@Component` (or at the wrong
  address), the app starts silently with no list — the same two-step check as for any
  missing bean.

### Where it is used

A real debugging tool: class seems ignored → grep the list; "2 beans matching" → the
list shows both names. Dev-time only — the inventory exposes internals, so it never
ships as a production endpoint.

### Link to the task

**S4** done (exact task code, run). **S5** done (your three found, `dataSource`
spotted, the two hundred strangers acknowledged and deferred to F6.9). Done-when:
*"you have printed the bean list"* ✔. The class stays until the final lesson, honouring
the task's "temporarily".

### Do it yourself (second laptop)

**Setup check:** converted scratch app runs; `pg` Up.

1. Create `BeanLister.java` in `scratch-app/src/main/java/com/zenith/catalog/` exactly
   as printed (package-private `class BeanLister` is fine).
2. `mvn spring-boot:run` → after `Started CatalogApplication`, the alphabetical list
   pours out.
3. Find all six: `beanLister`, `catalogApplication`, `dataSource`, `eventController`,
   `eventService`, `jdbcEventRepository`. Optional count:
   `mvn spring-boot:run | grep -c '^[a-zA-Z]'` → ~200.
4. Pick three strangers (`dispatcherServlet`, `jacksonObjectMapper`,
   `errorAttributes`) and say when each worked for you.
5. Leave the class in place; removal happens in the final lesson.

**Done when:** the list is on your screen, the six named beans are found, and the
sentence comes out naturally: the container is a list of objects, and I can see all of
them.

### Summary for revision

- `BeanLister` = `@Component` + `CommandLineRunner` + context injected + names printed
  sorted.
- Only beans get the `CommandLineRunner` call; the lister appears in its own list.
- 201 recipes in the tiny converted app: your three, Boot's `dataSource`, the
  application class, and ~196 strangers from auto-configuration (F6.9).
- The list shows bean names (small first letter), not class names.
- First diagnostic for "is my class a bean?": grep the list.

### Check questions, with answers

**Question 1.** (The `BeanLister` class as printed above; the app starts successfully.)
(a) Does `run` execute before or after the beans are cooked, and how does the
`CommandLineRunner` deal guarantee that order? (b) Will the string `beanLister` appear
in the list it prints? Why?

**Answer.** (a) After. The deal is "beans implementing this interface get `run` called
once startup **completes**" — and startup completing means all eager singletons are
already cooked. Spring cannot call a method on a bean it has not built, so the order is
structural: cook first, then run the runners. (b) Yes — line 5 of the real output. To
receive the call at all, `BeanLister` must be a bean, so its recipe is on the shelf,
so its name is in the array it prints. The census-taker counts itself.

**Question 2.** A colleague's class compiles and the app starts cleanly:

```java
package com.zenith.tools;

import org.springframework.stereotype.Service;

@Service
public class CleanupService {
    public void cleanup() { /* ... */ }
}
```

Main class: `com.zenith.catalog.CatalogApplication` (`@SpringBootApplication`). The bean
lister does **not** print `cleanupService`. Name the two things to check first, and the
culprit here.

**Answer.** Check one: does the class carry a stereotype mark? Yes — `@Service` is
present. Check two: does the class sit at or below the scanning root? The root is
`com.zenith.catalog`; the class sits in `com.zenith.tools` — a **sibling**. That is the
culprit: the walk never enters `com.zenith.tools`, the mark is never read, no recipe is
written, and the app starts happily without it (nothing depends on it — the silent
version). Fix: move the class under `com.zenith.catalog`.

---

## Lesson 15 — Break it on purpose: the package trap (subtask S6)

### Definition

A controlled accident: move `EventService` — correct, annotated, working — one folder
sideways, from `com.zenith.catalog` to `com.zenith.other`, changing nothing else about
it. Start the app and read the refusal, slowly, while already knowing the answer.

### Why it is needed

The scanning rule says: start at the package of the `@SpringBootApplication` class, walk
downward only. A sibling package is never visited, so its marks are never read and no
recipe cards are written. The silent version was proven with a probe nobody needed.
`EventService` is needed — `EventController`'s constructor demands one — so this time
the vanishing is loud. This crash is the most common Spring startup failure a beginner
meets; the task insists you cause it once yourself so the real one reads like an old
acquaintance.

### How the break works

1. The scanning root is fixed by `CatalogApplication`'s package: `com.zenith.catalog`.
   Moving `EventService` does not move the root.
2. The walk covers `com.zenith.catalog` and below. `com.zenith.other` sits **beside**
   the root. The walk never enters.
3. `@Service` on `EventService` is never seen → no recipe card. To Spring, the class
   does not exist.
4. Cooking: Spring builds `EventController`, reads its shopping list — one
   `EventService` — finds no card, and stops the whole startup with a report.

### The break, executed for real

`EventService.java` moved to `src/main/java/com/zenith/other/`, the entire file after
the move:

```java
package com.zenith.other;

import com.zenith.catalog.EventRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EventService {
    private final EventRepository repository;

    public EventService(EventRepository repository) {
        this.repository = repository;
    }

    public List<String> upcomingEventNames() {
        return repository.findAllNames();
    }
}
```

Classes in different packages must import each other by full name (same-package classes
need no import — which is why none of these imports existed before the move).

**First surprise — Java's gate, not Spring's.** The first run never reached Spring:

```text
[ERROR] COMPILATION ERROR :
[ERROR] .../com/zenith/catalog/WiringAttempt.java:[12,41] cannot find symbol
  symbol:   class EventService
  location: class com.zenith.catalog.WiringAttempt
```

`WiringAttempt` (the 3-line fake-repository test) also mentions `EventService`. The
compiler checks every reference and fails fast. Compile error and missing-bean error are
**two different gates**: Java's gate asks "does this class exist where you say it does?"
(compile time); Spring's gate asks "did the scan write a card for it?" (startup). You
can pass the first and still fail the second. Adding
`import com.zenith.other.EventService;` to `EventController` and `WiringAttempt` cleared
the first gate.

**Then Spring's gate.** The whole failure, exactly as printed:

```text
2026-10-29T01:30:00.126+05:30 ERROR 33489 --- [           main] o.s.b.d.LoggingFailureAnalysisReporter   :

***************************
APPLICATION FAILED TO START
***************************

Description:

Parameter 0 of constructor in com.zenith.catalog.EventController required a bean of type 'com.zenith.other.EventService' that could not be found.


Action:

Consider defining a bean of type 'com.zenith.other.EventService' in your configuration.
```

Same log, two more observations: no `Tomcat started on port 8080` line, and `BeanLister`
printed nothing — runners fire only after startup completes, and startup never
completed.

### Walkthrough — reading the whole report, including Action

| Piece of the message | What it tells you |
| --- | --- |
| `APPLICATION FAILED TO START` | Startup stopped during cooking. Nothing is running. |
| `Parameter 0 of constructor in com.zenith.catalog.EventController` | **Who** was being built and **which slot** could not be filled — the first constructor parameter. |
| `required a bean of type 'com.zenith.other.EventService'` | **What** was wanted — the full type, package and all. |
| `that could not be found` | No recipe card matched. Not "failed to build" — never found at all. |
| `Action: Consider defining a bean...` | Spring's best generic guess at a fix. |

The detail the task wants caught: **the address of the problem is printed inside the
message.** The wanted type is `com.zenith.other.EventService`; the root is
`com.zenith.catalog`. The error itself shows a class outside the scanned area — a reader
who knows the downward-only rule diagnoses this from the Description line alone.

The Action text ("define a bean") would technically work but treats the symptom. The
honest fix is the address: the class is marked correctly and merely lives in the wrong
place. The two-step check — **mark present? package under the root?** — beats following
the Action blindly.

### The fix, verified

File moved back to `com/zenith/catalog/`, package line restored, both added imports
deleted, empty `other` folder removed. Restart:

```text
$ curl http://localhost:8080/events
["Monsoon Rock Night","Jazz at the Note","Classical Evening","Open Mic Night","Sufi Night","Blues Brunch","Indie Fest"]
```

And `eventService` is back in `BeanLister`'s printed list.

### Important terms

| Term | Meaning |
| --- | --- |
| **Java's gate** | The compiler: every reference must resolve. Fails with `cannot find symbol` at compile time. |
| **Spring's gate** | The scan + cooking: a bean of the wanted type must exist. Fails with `required a bean ... could not be found` at startup. |
| **Action section** | The last block of a startup failure report — Spring's suggested fix. Read it, then verify against the two-step check. |

### Common mistakes

- **Panic-fixing with `new`.** `this.service = new EventService(...)` makes the error
  vanish — by reinstalling the hidden-`new` trap. The error is a location problem; fix
  the location.
- **Reading only the headline.** The Description names who, which slot, and what type —
  and the type's package is often the entire diagnosis.
- **Confusing the gates.** `cannot find symbol` = compiler, fix imports/names.
  `required a bean ... could not be found` = scan, fix mark or package.

### Where it is used

Every package reorganisation risks this failure: moving classes between folders,
renaming packages, extracting a second top-level package. Teams meet it most often right
after a refactor — which is exactly when the full type name inside the Description pays
for itself.

### Link to the task

**S6 complete:** moved sideways, started, full failure read including Action, moved
back, verified. With it, every "done when" of F6.1 is satisfied: no `new` wiring your
own classes ✔, bean list printed ✔, **package failure caused and fixed ✔**.

### Do it yourself (second laptop)

**Setup:** converted scratch app working, `pg` container Up.

1. `mkdir -p scratch-app/src/main/java/com/zenith/other`, move `EventService.java`
   there, change its package line to `com.zenith.other`, add
   `import com.zenith.catalog.EventRepository;` to it.
2. `mvn spring-boot:run` → expect the **compile error** `cannot find symbol: class
   EventService` naming each file that mentions it. Add
   `import com.zenith.other.EventService;` to every file listed
   (`EventController.java`, `WiringAttempt.java`).
3. Run again → expect `APPLICATION FAILED TO START` with the Description and Action
   above. Read every line. Confirm: no Tomcat line, no bean list.
4. Reverse everything: file back, `package com.zenith.catalog;` restored, both added
   imports deleted, empty folder removed.
5. Run again; `curl http://localhost:8080/events` must return the seven names.

**Done when:** you caused the failure, pointed at the package name *inside* the error
message as the clue, and recovered to a working `/events`.

### Summary for revision

- Moving a class sideways from the root removes it from the scan; if something depends
  on it, startup dies with `APPLICATION FAILED TO START`.
- The Description names who (class + parameter slot), what (full wanted type), and —
  via the type's package — where the problem lives.
- Two gates: compiler (`cannot find symbol`, references) vs Spring (`could not be
  found`, recipe cards). Passing one does not pass the other.
- Runners (`BeanLister`) never fire on a failed startup; no Tomcat line either.
- Fix the address, not the symptom; never patch with `new`.

### Check questions, with answers

**Question 1.** An app's main class is `com.shop.ShopApplication`
(`@SpringBootApplication`). Startup fails with exactly:

```text
***************************
APPLICATION FAILED TO START
***************************

Description:

Parameter 0 of constructor in com.shop.web.OrderController required a bean of type 'com.shop.pricing.PriceService' that could not be found.

Action:

Consider defining a bean of type 'com.shop.pricing.PriceService' in your configuration.
```

Using only this text: who was being built, what was missing, is the missing class inside
or outside the scanned area — and what is your first check therefore?

**Answer.** Being built: `com.shop.web.OrderController`, stuck on its first constructor
parameter. Missing: a bean of type `com.shop.pricing.PriceService`. Scanned area: the
root is `com.shop` (the main class's package), and `com.shop.pricing` sits **below**
it — so the class is **inside** the scanned area. The package is not the culprit this
time, which flips the two-step check to its other half: open `PriceService` and look for
the stereotype mark (`@Service` etc.) — the likely finding is that the mark is missing.
(If the mark is present and the package is right, the next suspect is that
`PriceService` is an interface with no marked implementing class — an interface slot is
filled by a bean that implements it.)

**Question 2.** During the break, the project failed twice: first with
`cannot find symbol: class EventService` (before the extra imports), then — after adding
imports — with `required a bean of type 'com.zenith.other.EventService' that could not
be found`. Both say "EventService cannot be found" in spirit. Which tool produced each
message, at what moment, and what does each failure actually prove about the class?

**Answer.** The first came from the **Java compiler**, at compile time, before Spring
ever ran. It proves a source-code problem: some file (`WiringAttempt.java`, line 12)
references the name `EventService`, and no class by that name is visible from that
location — an import/visibility issue, nothing to do with beans. The second came from
**Spring**, at startup, after compilation fully succeeded. It proves the opposite kind
of problem: the class exists and every reference resolves, but the scan never wrote a
recipe card for it, so no bean of that type was on the shelf when `EventController`'s
slot needed filling. Java's gate checks that references resolve; Spring's gate checks
that a bean exists. The move tripped both, one after the other, and each gate reported
in its own language.

---

## Lesson 16 — `@Component` vs `@Service` vs `@Repository` (subtask S8)

### Definition

The scratch app wears three marks: `@RestController`, `@Service`, `@Repository`. To the
scanner they are the same thing: each carries `@Component` inside, and carrying
`@Component` is the one condition for a recipe card. The differences are a readable
label for humans — plus one real extra behaviour on two of them.

### Why it is needed

Without this, the marks feel like magic words — "what breaks if I pick wrong?" After
it, choosing a mark takes zero thought, and reading someone else's choice tells you
something true about their code.

### How it works

- **The scanner's one question:** does this class carry `@Component`, directly or hidden
  inside another mark? If yes → card. Identical for all four marks.
- **"Hidden inside" is literal:** the `@Service` annotation is itself a small piece of
  code annotated with `@Component`. A service *is* a component, by declaration. Same for
  `@Repository`, and (via one extra hop) `@RestController`.

| Mark | Layer it names for humans | Extra behaviour today |
| --- | --- | --- |
| `@Component` | none — the generic base mark | none |
| `@Service` | business rules | none — a pure readable label |
| `@Repository` | database access | database-error translation |
| `@RestController` | web layer | real: methods become URL handlers, return values become JSON |

### The swap, run for real

One word changed in `EventService.java` (plus its import):

```java
package com.zenith.catalog;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EventService {
    private final EventRepository repository;

    public EventService(EventRepository repository) {
        this.repository = repository;
    }

    public List<String> upcomingEventNames() {
        return repository.findAllNames();
    }
}
```

Real results with `@Component`:

```text
$ curl http://localhost:8080/events
["Monsoon Rock Night","Jazz at the Note","Classical Evening","Open Mic Night","Sufi Night","Blues Brunch","Indie Fest"]
```

…and the bean list still contains `eventService`. Proves two things: card-writing is
identical, and **the bean name comes from the class name, not the mark**
(`EventService` → `eventService`, whichever annotation sits on top). `@Service` was
restored afterwards and the app re-verified.

### Walkthrough — looking inside `@Service` itself

`javap` (ships with the JDK) prints what is inside a compiled `.class` file. Pointed at
Spring's own `Service` class inside `spring-context-6.2.19.jar`:

```text
$ javap -v -cp spring-context-6.2.19.jar org.springframework.stereotype.Service
...
RuntimeVisibleAnnotations:
  0: java.lang.annotation.Target(value=[TYPE])
  1: java.lang.annotation.Retention(value=RUNTIME)
  2: java.lang.annotation.Documented
  3: org.springframework.stereotype.Component
```

Entry 3 is the whole story: `@Service` is itself annotated with `@Component`. The
scanner finds `@Service`, looks inside, finds `@Component`, writes the card. No special
case for services exists.

**The two real extras:**

- **`@Repository` — database-error translation.** Different databases report the same
  problem (say, a duplicate key) with different error classes and codes. Spring keeps
  one shared family of error types; `@Repository` says *this class talks to the
  database — translate its database-specific failures into the shared family.* Our
  repository catches `SQLException` by hand, so the feature stays invisible for now; it
  matters with the database tools of later tasks. It is the behavioural reason
  `@Repository` is right for `JdbcEventRepository`.
- **`@RestController` — real web behaviour.** `@GetMapping("/events")` became a live
  URL, and the returned `List<String>` became a JSON array (the `jacksonObjectMapper`
  stranger did the conversion). Genuine machinery, not a label.

### Important terms

| Term | Meaning |
| --- | --- |
| **Stereotype mark family** | `@Component` plus the marks that carry it inside: `@Service`, `@Repository`, `@RestController`. |
| **Bean name rule** | Class name with a small first letter — independent of which mark is used. |
| **`javap`** | JDK tool that prints the contents of a compiled `.class` file, including its annotations. |

### Common mistakes

- **Using `@Component` for everything.** Works forever, but throws away free
  documentation. A class full of business rules deserves `@Service`.
- **Believing `@Service` adds hidden powers.** It adds nothing today; if a bean "isn't
  working", swapping marks fixes nothing — the two-step check (mark present? package
  under root?) is where answers live.
- **Putting the mark on an interface.** The scanner writes cards only for concrete
  classes; `@Service` on the `EventRepository` interface would register nothing. The
  mark belongs on the implementing class (`JdbcEventRepository`).

### Where it is used

The marks are the fastest map of a real codebase: the tags tell you each class's layer
before you open the file. Reviewers lean on them; tools can target "all repositories".
Picking the honest mark is a courtesy to every future reader.

### Link to the task

**S8 complete:** comparison made with a running swap, "identical core effect" proven by
`curl` and the bean list, the two real extras named. The scratch app's three marks are
justified choices, not habits.

### Do it yourself (second laptop)

**Setup:** converted scratch app working, `pg` Up.

1. In `EventService.java`: `@Service` → `@Component`, import
   `org.springframework.stereotype.Service` → `...stereotype.Component`.
2. `mvn spring-boot:run`, then `curl http://localhost:8080/events` → same seven names;
   find `eventService` in the bean list.
3. Look inside `@Service`:

   ```bash
   JAR=$(find ~/.m2 -name "spring-context-*.jar" | head -1)
   javap -v -cp "$JAR" org.springframework.stereotype.Service | tail -16
   ```

   Expect the last annotation: `org.springframework.stereotype.Component`.
4. Restore `@Service` and its import; run and `curl` once more.

**Done when:** the swap changed nothing, you saw `@Component` inside `@Service`, and
you can name the two marks with real extra behaviour.

### Summary for revision

- One scanner question: does the class carry `@Component`, directly or inside another
  mark? All four marks pass it the same way.
- Bean name = class name with small first letter, regardless of mark.
- `@Service` = pure label; `@Repository` = label + database-error translation;
  `@RestController` = label + real web machinery; `@Component` = the generic base.
- Marks go on concrete classes, never interfaces.
- Pick by layer: web → `@RestController`, business rules → `@Service`, database →
  `@Repository`, anything else → `@Component`.

### Check questions, with answers

**Question 1.** The app root is `com.shop`. A teammate says: *"The `billingService`
bean was never created because we wrote `@Component` instead of `@Service` — wrong
annotation for a business class."* The class compiles:

```java
package com.shop.billing;

import org.springframework.stereotype.Component;

@Component
public class BillingService {
    public int totalFor(String customer) { return 0; }
}
```

Can the teammate's explanation be correct? Say what `@Component` vs `@Service` changes
about bean creation, and — if the bean truly is missing — name the checks you would run
instead.

**Answer.** No — the explanation is impossible. `@Component` and `@Service` are
identical to the scanner: `@Service` is `@Component` underneath, both produce the same
recipe card, and the bean name (`billingService`) would be the same either way. The
choice changes readability for humans, never bean creation. If the bean truly is
missing, run the two-step check: is a mark present? (yes, `@Component`); is the package
under the root? (`com.shop.billing` sits below `com.shop` — yes). Both pass, so look
further: is `BillingService` really a concrete class (not an interface or abstract)?
And is the bean really missing, or is the symptom something else entirely? Grep the
bean list for `billingService` to settle it with evidence.

**Question 2.** Two new classes need marks. `InvoiceCalculator` contains pure business
rules (no database, no web). `CustomerJdbcStore` has a constructor taking `DataSource`
and runs SQL inside. Which mark belongs on each, and why? Then name which marks in the
family carry real extra behaviour today, and what that behaviour is.

**Answer.** `InvoiceCalculator` → `@Service`: it is business rules, and `@Service` is
the label that says so; it adds no behaviour, but tells every reader the layer.
`CustomerJdbcStore` → `@Repository`: it talks to the database, and the mark both
documents that and switches on database-error translation for the class. The real
extras in the family: `@RestController` (methods become URL handlers; return values
become JSON) and `@Repository` (database-specific failures translated into Spring's
shared error family). `@Service` and `@Component` carry no extra behaviour — their
difference is purely what they tell human readers.

---

## Lesson 17 — Failure drills, the checkpoint, and the cleanup (subtask S10 + CP)

### Definition

The task's final Ask-AI prompt — *"Give me three different reasons a class with
`@Service` might still not become a bean, and show me the error message each one
produces"* — answered the honest way: all three failures caused for real in the scratch
app, each error captured from an actual run. Then the promised cleanup (`BeanLister` was
"temporary"), the final audit of "done when", and the four checkpoint questions.

### Why it is needed

"The annotation is on the class, yet the bean is not there" is a puzzle that will recur
for years. The three reasons below cover nearly every real case, and each prints a
**differently shaped message**. Telling the shapes apart at a glance turns an evening of
confusion into seconds of diagnosis.

### The three reasons, with real error messages

**Reason 1 — the class is outside the scanned area.** Mark present, but the scan starts
at the `@SpringBootApplication` class's package and walks downward only; a sibling
package is never visited, no card is written. Caused by moving `EventService` from
`com.zenith.catalog` to `com.zenith.other`:

```text
***************************
APPLICATION FAILED TO START
***************************

Description:

Parameter 0 of constructor in com.zenith.catalog.EventController required a bean of type 'com.zenith.other.EventService' that could not be found.

Action:

Consider defining a bean of type 'com.zenith.other.EventService' in your configuration.
```

The service is on the **right-hand side** — the thing that could not be found — and its
printed package (`com.zenith.other`) is the diagnosis. (With no dependents, this reason
produces no error at all: the bean is just silently absent from the list.)

**Reason 2 — the service's own ingredient is missing.** Mark fine, package fine, card
written — but cooking fails: the constructor demands something the box lacks. Caused by
removing `@Repository` from `JdbcEventRepository`:

```text
***************************
APPLICATION FAILED TO START
***************************

Description:

Parameter 0 of constructor in com.zenith.catalog.EventService required a bean of type 'com.zenith.catalog.EventRepository' that could not be found.

Action:

Consider defining a bean of type 'com.zenith.catalog.EventRepository' in your configuration.
```

Same headline, **opposite role**: the service is on the **left-hand side** — the one
being built while something else is missing. Left = victim, right = missing.

**Reason 3 — a dependency cycle.** Both classes marked and carded, each demanding the
other in its constructor; neither can be built first, so neither bean ever exists. The
pair used:

```java
package com.zenith.catalog;

import org.springframework.stereotype.Service;

@Service
public class AlphaService {
    private final BetaService beta;

    public AlphaService(BetaService beta) {
        this.beta = beta;
    }
}
```

```java
package com.zenith.catalog;

import org.springframework.stereotype.Service;

@Service
public class BetaService {
    private final AlphaService alpha;

    public BetaService(AlphaService alpha) {
        this.alpha = alpha;
    }
}
```

```text
***************************
APPLICATION FAILED TO START
***************************

Description:

The dependencies of some of the beans in the application context form a cycle:

┌─────┐
|  alphaService defined in file [.../target/classes/com/zenith/catalog/AlphaService.class]
↑     ↓
|  betaService defined in file [.../target/classes/com/zenith/catalog/BetaService.class]
└─────┘

Action:

Relying upon circular references is discouraged and they are prohibited by default. Update your application to remove the dependency cycle between beans. As a last resort, it may be possible to break the cycle automatically by setting spring.main.allow-circular-references to true.
```

The honest fix is design, not settings: a cycle means two classes are so entangled that
neither can exist first — usually the shared logic wants a third class both depend on.
(Demo files deleted after the capture.)

### Telling the three apart at a glance

| What the Description shows | Reason | First move |
| --- | --- | --- |
| Your `@Service` on the **right** ("required a bean of type '…YourService'") | Outside the scanned area | Read the package in the message; compare with the root |
| Your `@Service` on the **left** ("Parameter 0 of constructor in …YourService required…") | Its own ingredient is missing | Hunt the type on the right with the two-step check |
| A box diagram with arrows | Dependency cycle | Redesign — extract the shared piece into a third class |

Near-cousin already met: `required a single bean, but 2 were found` — the bean *was*
created, but two candidates fought over one slot. Too many, not too few.

### The cleanup and the final audit, run for real

`BeanLister.java` deleted (its "temporarily" honoured). Final verification run:

```text
$ curl http://localhost:8080/events
["Monsoon Rock Night","Jazz at the Note","Classical Evening","Open Mic Night","Sufi Night","Blues Brunch","Indie Fest"]
```

No bean list prints anymore, and the folder holds exactly seven files:
`CatalogApplication`, `EventController`, `EventService`, `EventRepository`,
`JdbcEventRepository`, `FakeEventRepository`, `WiringAttempt`.

**Done when, item by item:**

| Requirement | Evidence |
| --- | --- |
| App starts with no `new` wiring your own classes | Search for `new EventService` / `new JdbcEventRepository` / `new EventController` in the app finds nothing; the only `new`s live in `WiringAttempt`, the *test*, where hand-building with a fake is the point |
| Bean list printed | 201 names; `eventController`, `eventService`, `jdbcEventRepository` found among them |
| Out-of-scanned-package failure caused and fixed | The sideways move, the full report read including Action, recovery to a working `/events` |

**Task F6.1 complete.**

### Common mistakes

- **Reading only `APPLICATION FAILED TO START`.** Every diagnosis above came from the
  Description line — who is left, what is right, or a diagram.
- **"Fixing" a cycle with `spring.main.allow-circular-references=true`.** The Action
  text itself says *last resort*: it hides a design problem; the entanglement stays.
- **Mixing up the two could-not-be-found shapes.** Reason 1 and reason 2 print
  near-identical text with opposite meanings.

### Where it is used

These three messages are the daily bread of Spring debugging — after any refactor
(reason 1), any forgotten mark on a helper (reason 2), any over-coupled design
(reason 3). Reading the Description line fixes them in minutes.

### Do it yourself (second laptop)

**Setup:** converted scratch app working, `pg` Up.

1. **Drill reason 2:** comment out `@Repository` in `JdbcEventRepository.java`, run,
   confirm `EventService` is on the *left* of the message. Restore the mark.
2. **Drill reason 3:** create `AlphaService.java` and `BetaService.java` exactly as
   above, run, see the diagram, delete both files.
3. (Reason 1 was drilled in the package-trap exercise.)
4. Delete `BeanLister.java`. Run; app starts, no list prints,
   `curl http://localhost:8080/events` returns the seven names.
5. Run the Ask-AI prompt yourself and compare its answer against these drills.

**Done when:** reasons 2 and 3 caused by your own hands, lister gone, `/events` still
answering, each failure shape recognisable at a glance.

### Summary for revision

- Three reasons a marked class still fails: outside the scan (your class on the
  *right* of the message), missing ingredient (your class on the *left*), dependency
  cycle (the diagram).
- A cycle is a design message; the settings switch is a last resort that hides it.
- `required a single bean, but 2 were found` is the opposite disease: too many.
- Diagnosis always starts at the Description line, never the headline.

### Checkpoint questions, with answers

**Question 1.** What is a bean, and what is the application context, in one sentence
each?

**Answer.** A bean is an object that Spring built from a recipe and keeps in its box,
handing the same shared instance to everyone who asks (one instance by default). The
application context is that box: a real object holding every bean, indexed by name and
type, which fills constructor slots during startup and answers `getBean` requests after.

**Question 2.** Your controller worked perfectly with `new` inside it. Describe
precisely what you could not do, and why that mattered.

**Answer.** It could not be given a fake repository for a test. The `new` chain inside
the controller locked in the real implementations: its constructor took no parameters,
so there was no door to hand anything through, and building it meant building the real
`JdbcEventRepository`, which demanded a live Postgres. A three-line test became
impossible without a running database. It mattered because swapping parts — fake for
test, new implementation later — requires the choice of parts to be made *outside* the
class, and `new` inside the class makes the choice internal and permanent.

**Question 3.** A colleague's `@Service` class is not being found. Name two things you
would check first.

**Answer.** First: does the class actually carry a stereotype mark (`@Service`,
`@Component`, `@Repository`, `@RestController`) — on the concrete class, not an
interface? Second: does its package sit at or below the scanning root, the package of
the `@SpringBootApplication` class? A sibling or parent package is never scanned, and
the full type name printed in the error usually answers this check by itself.

**Question 4.** What does `@Repository` do that `@Component` does not?

**Answer.** Two things. For humans, it names the layer: this class talks to the
database. For behaviour, it switches on database-error translation: database-specific
failures are translated into Spring's one shared family of error types, so the rest of
the app can handle "duplicate key" the same way on any database. The recipe card
itself is identical — `@Repository` carries `@Component` inside.

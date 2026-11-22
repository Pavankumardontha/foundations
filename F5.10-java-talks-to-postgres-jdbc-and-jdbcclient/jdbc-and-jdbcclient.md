# F5.10 — Java talks to Postgres: raw JDBC and JdbcClient

Notes for Task F5.10. One lesson per section. Each lesson ends with its understanding-check
questions and full answers.

**Prerequisite machine state (from Module F5):** a Docker container named `pg` running
Postgres 17, with a volume so data survives, and a database named `zenith` inside it. The
`zenith` database has three tables used throughout this task:

- `venue` — `id uuid`, `name text`, `city text`, `capacity integer`, and more.
- `event` — `id uuid`, `venue_id uuid` (points at `venue.id`), `name text`, `starts_at timestamptz`.
- `price_tier` — `id uuid`, `event_id uuid` (points at `event.id`), `name text`, `amount numeric(19,4)`.

Sample rows we query: venues in `Hyderabad` (Riverside Arena), `Mumbai` (The Blue Note), and
`Chennai` (Lakeside Hall), with seven events spread across them.

The connection facts for every example in this task:

| Thing | Value |
| --- | --- |
| Host | `localhost` |
| Port | `5432` |
| Database | `zenith` |
| User | `postgres` |
| Password | `dev` |

---

## Lesson 1 — What JDBC is, and why it exists

### Definition

**JDBC** stands for **Java Database Connectivity**. It is a fixed set of Java types for talking
to a relational database. A **relational database** is a program that stores data in tables of
rows and columns; Postgres is one such program. Think of JDBC as one agreed "language" your
Java code speaks to any database.

### Why it is needed

Imagine there were no shared standard. Then Postgres would need one set of Java commands.
Another database, like MySQL, would need a totally different set. Your code would be glued to
one database forever. Switching databases would mean rewriting every line that touches data.

JDBC removes that pain. It defines one standard way to connect, send a query, and read
results. Every relational database agrees to support it. So your Java code looks the same no
matter which database sits underneath.

### Key points

- JDBC is made of **interfaces** — lists of method promises with no code inside them.
- The interfaces ship inside Java itself, in the module `java.sql`. You do not download them.
- The real code behind the promises is the **driver**, and it is specific to one database.
- Your code only ever talks to the interfaces; the driver does the real work underneath.
- Every higher-level Java database tool (JPA, Hibernate, Spring's `JdbcClient`) sits on JDBC.

### How it works, step by step

1. Java gives you the JDBC **interfaces**: `Connection`, `PreparedStatement`, `ResultSet`, and
   a few more. An **interface** is a list of method names with no actual code inside. It says
   *what* you can do, not *how* it is done.
2. The interfaces alone do nothing. Someone must write the real code behind the promises.
3. That real code is the **driver**. It is specific to one database. The Postgres driver keeps
   JDBC's promises using Postgres's own network protocol.
4. Your program calls methods on the interfaces. Underneath, the driver's objects actually run.

### Example

The smallest program that proves the core claim. It asks Java three questions about the three
main JDBC types: are they interfaces, and where do they come from?

```java
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

// Lesson 1 demo: JDBC is a set of INTERFACES that ship inside Java itself.
// The real working objects are supplied later by a database driver.
public class JdbcInterfacesDemo {
    public static void main(String[] args) {
        Class<?>[] jdbcTypes = { Connection.class, PreparedStatement.class, ResultSet.class };
        for (Class<?> t : jdbcTypes) {
            System.out.println(
                t.getName()
                + "  | is it an interface? " + t.isInterface()
                + "  | ships in Java module: " + t.getModule().getName());
        }
    }
}
```

Real output on Java 21:

```text
java.sql.Connection  | is it an interface? true  | ships in Java module: java.sql
java.sql.PreparedStatement  | is it an interface? true  | ships in Java module: java.sql
java.sql.ResultSet  | is it an interface? true  | ships in Java module: java.sql
```

### Walkthrough

1. The three `import java.sql.*` lines pull in the JDBC types. The package name starts with
   `java.`, which means these types are part of Java itself, not something downloaded.
2. `Class<?>[] jdbcTypes = { Connection.class, ... }` puts the three types in a small array so
   each can be asked the same questions.
3. `t.isInterface()` asks Java at runtime: "is this an interface?" All three answer `true`.
4. `t.getModule().getName()` asks "which part of Java owns this type?" All three answer
   `java.sql`.
5. Every line of output says `interface? true` and `module: java.sql`. So these are promises
   (no code inside), and they come built into Java. Nothing here knows about Postgres yet —
   that missing piece is the driver (Lesson 2).

### Important terms

| Term | Meaning |
| --- | --- |
| **JDBC** | Java Database Connectivity. A fixed set of Java interfaces for talking to any relational database. |
| **Relational database** | A program that stores data in tables of rows and columns. Postgres is one. |
| **Interface** | A list of method names with no code inside. A promise of *what*, not *how*. |
| **`java.sql`** | The module inside Java that holds the JDBC interfaces. |
| **Driver** | Database-specific code that implements the JDBC interfaces. Covered in Lesson 2. |

### Common mistakes

- **Thinking you install JDBC.** You do not. The interfaces are already inside Java. What you
  add is the driver. There is no "JDBC library" to download.
- **Thinking JDBC is one product.** It is not a program or a jar. It is a set of interfaces
  plus the rule that drivers implement them.
- **Confusing JDBC with the driver.** JDBC is the promise. The driver is the Postgres-specific
  code that keeps the promise. Keep the two apart.

### Where it is used

JDBC sits at the bottom of every Java database tool. Big frameworks like JPA and Hibernate call
JDBC underneath. Spring's `JdbcClient` calls JDBC underneath. All of them eventually open a
`Connection`, send a `PreparedStatement`, and read a `ResultSet`. Seeing the plumbing once by
hand is what turns those tools from magic into convenience.

### Link to the task

This is the mental model under the whole task. Subtask 2 has you write raw JDBC by hand: that
code is nothing but `Connection`, `PreparedStatement`, and `ResultSet` used for real. When you
later compare raw JDBC with `JdbcClient` (subtask 7), the thing that stays constant underneath
both is JDBC.

### Do it yourself — verify Java and run the demo (blank laptop)

This needs only Java. No database and no driver yet.

**Step 0 — Install Java 21 (Mac).**

```bash
brew install openjdk@21
echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
java -version
```

Expected (the version line must start with 21):

```text
openjdk version "21.0.11" 2026-04-21
```

If `java -version` says "command not found", the PATH line did not take — open a new terminal
and run `source ~/.zshrc` again.

**Step 1 — Make a folder and the file.**

Create a folder, then create a file `JdbcInterfacesDemo.java` with exactly the code from the
Example above.

**Step 2 — Compile.**

```bash
javac JdbcInterfacesDemo.java
```

Success prints nothing and creates `JdbcInterfacesDemo.class`. If you see
`error: ... class ... is public, should be declared in a file named JdbcInterfacesDemo.java`,
the file name does not match the class name — rename the file.

**Step 3 — Run.**

```bash
java JdbcInterfacesDemo
```

Expected output:

```text
java.sql.Connection  | is it an interface? true  | ships in Java module: java.sql
java.sql.PreparedStatement  | is it an interface? true  | ships in Java module: java.sql
java.sql.ResultSet  | is it an interface? true  | ships in Java module: java.sql
```

**Done when:** you see three lines, each saying `interface? true` and `module: java.sql`.

### Summary for revision

- JDBC = one standard set of Java interfaces for any relational database.
- The interfaces ship inside Java (`java.sql`); you never download them.
- A driver provides the real code behind the interfaces, for one specific database.
- Your code talks to interfaces; the driver does the work. Every higher tool sits on JDBC.

### Check questions, with answers

**Question 1.** A teammate says: "To use Postgres from Java, first download and install the
JDBC library, because JDBC is a separate program you run." Two things in that sentence are
wrong. What are they, and what is the correct version?

**Answer.** First error: there is no "JDBC library" to download — the JDBC interfaces already
ship inside Java, in the module `java.sql`. Second error: JDBC is not a separate program you
run — it is a set of interfaces (method promises) plus the rule that database drivers implement
them. The correct version: JDBC is already present in Java; the only thing you add for Postgres
is the Postgres **driver**, which supplies the real code behind the JDBC interfaces.

**Question 2.** We proved that `java.sql.Connection` is an interface (method promises with no
code inside) that ships inside Java in the module `java.sql`. If `Connection` has no real code
inside it, then when your program later opens a real connection to Postgres, where does the
actual working code behind that `Connection` come from?

**Answer.** From the Postgres **driver**. The `Connection` interface only promises the methods;
the driver (the `org.postgresql` jar you add to the project) supplies the real object that keeps
those promises, using Postgres's own network protocol. Your code holds a `Connection` reference
and calls its methods, but the object behind it is the driver's code.

### Your doubts

None recorded for this lesson.

---

## Lesson 2 — The driver, and the Postgres dependency

### Definition

A **driver** is a library of database-specific code that implements the JDBC interfaces for one
database. It comes as a **jar** file — a single file that bundles compiled Java code, like a zip
of a library. The Postgres driver's official name is `org.postgresql:postgresql`. In plain
words: JDBC is the empty promise; the driver is the code that keeps the promise, for Postgres.

### Why it is needed

The JDBC types like `Connection` are interfaces — lists of method names with no code inside. So
`Connection` promises methods but runs nothing. Without a driver, Java has no code that knows how
to talk to Postgres over the network. Ask Java to connect to a `jdbc:postgresql://...` address
and it finds nobody who speaks Postgres, so it fails. The driver is that missing code.

### Key points

- The driver is a jar you add to the project; it is specific to one database.
- A JDBC driver on the classpath **registers itself** — you write no registration code.
- The driver recognises addresses that start with `jdbc:postgresql:` and builds the real objects.
- Your variable's type is the interface `java.sql.Connection`; the real object is the driver's
  `org.postgresql.jdbc.PgConnection`.

### How it works, step by step

1. You put the driver jar on the **classpath** — the list of places Java searches for compiled
   code when it runs your program.
2. The driver registers itself automatically. Inside the jar is a small service file
   (`META-INF/services/java.sql.Driver`) naming the driver class. Java reads it and loads the
   driver. No registration code needed (this is JDBC 4.0+ behaviour).
3. When you ask to connect with an address starting `jdbc:postgresql:`, Java asks each registered
   driver "do you recognise this?" The Postgres driver says yes.
4. That driver builds the real `Connection` object and hands it back.

### Example

Program one asks which drivers are registered. Run without, then with, the driver jar.

```java
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Enumeration;

public class DriverCheck {
    public static void main(String[] args) {
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        if (!drivers.hasMoreElements()) {
            System.out.println("No JDBC drivers are registered.");
            return;
        }
        while (drivers.hasMoreElements()) {
            Driver d = drivers.nextElement();
            System.out.println("Registered driver: " + d.getClass().getName()
                + "  (version " + d.getMajorVersion() + "." + d.getMinorVersion() + ")");
        }
    }
}
```

```text
### DriverCheck WITHOUT the driver jar:
No JDBC drivers are registered.

### DriverCheck WITH the driver jar:
Registered driver: org.postgresql.Driver  (version 42.7)
```

Program two tries to open a connection. The line `DriverManager.getConnection(...)` means "open
one live conversation with the database" (covered fully in Lesson 4).

```java
import java.sql.Connection;
import java.sql.DriverManager;

public class ConnectWithoutDriver {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/zenith";
        try {
            Connection c = DriverManager.getConnection(url, "postgres", "dev");
            System.out.println("Connected. Real object behind the Connection interface: "
                + c.getClass().getName());
            c.close();
        } catch (Exception e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
        }
    }
}
```

```text
### ConnectWithoutDriver WITHOUT the driver jar:
java.sql.SQLException: No suitable driver found for jdbc:postgresql://localhost:5432/zenith

### ConnectWithoutDriver WITH the driver jar:
Connected. Real object behind the Connection interface: org.postgresql.jdbc.PgConnection
```

### Walkthrough

1. `DriverCheck` without the jar: the registered-drivers list is empty. Java has the interfaces
   but no driver behind them.
2. `DriverCheck` with the jar: the Postgres driver loaded and registered itself, printing
   `org.postgresql.Driver` version 42.7 — with no registration code written.
3. `ConnectWithoutDriver` without the jar: `No suitable driver found for jdbc:postgresql://...`.
   Java found no registered driver that handles `jdbc:postgresql:`. This is the most common first
   error in the whole task.
4. `ConnectWithoutDriver` with the jar: it connects, and the real class behind the `Connection`
   is `org.postgresql.jdbc.PgConnection` — proof that the driver supplies the real object.

### Important terms

| Term | Meaning |
| --- | --- |
| **Driver** | Database-specific code implementing the JDBC interfaces, shipped as a jar. |
| **Jar** | One file bundling compiled Java code, like a zip of a library. |
| **Classpath** | The list of places Java searches for compiled code at run time. |
| **`org.postgresql.Driver`** | The Postgres driver class that registers itself. |
| **`org.postgresql.jdbc.PgConnection`** | The real `Connection` object the Postgres driver builds. |
| **Auto-registration** | JDBC 4.0+ loading a driver just from its presence on the classpath. |

### Common mistakes

- **Forgetting the driver.** You get `No suitable driver found for jdbc:postgresql://...`. Fix:
  put the Postgres driver on the classpath.
- **Copying old tutorials that call `Class.forName("org.postgresql.Driver")`.** Required before
  JDBC 4.0; modern drivers register themselves, so it is unnecessary now (harmless, but noise).
- **Confusing two class names.** `org.postgresql.Driver` is the driver (it registers).
  `org.postgresql.jdbc.PgConnection` is the connection object it makes. Different jobs.
- **Wrong classpath punctuation.** Mac/Linux separate classpath entries with `:`; Windows with
  `;`. The wrong one means Java silently does not find the jar.

### Where it is used

Every Java app that talks to a real database ships that database's driver. Postgres app: the
`org.postgresql` driver. MySQL app: the MySQL driver. The JDBC code you write stays the same;
only the driver jar and the address change.

### Link to the task

Subtask 1 adds the `org.postgresql:postgresql` driver. The task note says the raw-JDBC step needs
*only* the driver, not the Spring starter — because raw JDBC is just the built-in interfaces plus
the driver. So the next several lessons need nothing but Java and this one jar.

### Do it yourself — get the driver and prove it registers (blank laptop)

Needs Java (from Lesson 1). No database required for this check.

**Step 1 — Download the driver jar.**

```bash
curl -sSL -o postgresql-42.7.4.jar \
  https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar
ls -la postgresql-42.7.4.jar
```

Expected: a file about 1 MB in size. If `curl` prints nothing and the file is missing, your
network blocked the download — try again, or use any Maven Central mirror.

**Step 2 — Create `DriverCheck.java`** with the code from the Example above.

**Step 3 — Compile.**

```bash
javac DriverCheck.java
```

**Step 4 — Run WITHOUT the driver on the classpath.**

```bash
java DriverCheck
```

Expected:

```text
No JDBC drivers are registered.
```

**Step 5 — Run WITH the driver on the classpath.**

```bash
java -cp .:postgresql-42.7.4.jar DriverCheck
```

(Windows: use `java -cp ".;postgresql-42.7.4.jar" DriverCheck`.)

Expected:

```text
Registered driver: org.postgresql.Driver  (version 42.7)
```

**Done when:** the driver is reported as registered only when the jar is on the classpath.

### Summary for revision

- A driver is a jar of database-specific code implementing the JDBC interfaces.
- On the classpath it registers itself (service file), so no `Class.forName` is needed.
- No driver → `No suitable driver found`. With driver → a real `PgConnection` appears.
- Same JDBC code, different driver jar per database. Raw JDBC needs only Java + the driver.

### Check questions, with answers

**Question 1.** You run a program containing
`DriverManager.getConnection("jdbc:postgresql://localhost:5432/zenith", "postgres", "dev")`
but did not put the Postgres driver jar on the classpath. It prints
`java.sql.SQLException: No suitable driver found for jdbc:postgresql://localhost:5432/zenith`.
Why did this happen, and what single change fixes it?

**Answer.** No driver was on the classpath, so no registered driver recognised the
`jdbc:postgresql:` address, and `DriverManager` gave up with `No suitable driver found`. The
built-in JDBC interfaces cannot talk to Postgres by themselves. The single fix: put the Postgres
driver on the classpath — at the command line `-cp .:postgresql-42.7.4.jar`, or as the
`org.postgresql:postgresql` dependency in a build file.

**Question 2.** You never write `Class.forName("org.postgresql.Driver")` and never write
`new org.postgresql.Driver()`, yet with the jar on the classpath `DriverManager` reports
`org.postgresql.Driver` as registered. How does the driver get registered with no registration
code from you?

**Answer.** The driver jar contains a service file at `META-INF/services/java.sql.Driver` that
names `org.postgresql.Driver`. When `DriverManager` initialises, Java's ServiceLoader reads that
file from every jar on the classpath and loads and registers each named driver automatically.
This is JDBC 4.0+ auto-registration, which is why the old `Class.forName(...)` line is no longer
needed.

### Your doubts

None recorded for this lesson.

---

## Lesson 3 — The JDBC URL, user, and password (the database's address)

### Definition

The **JDBC URL** is a single line of text that says where the database is and which one to open.
Its Postgres shape is `jdbc:postgresql://host:port/database`. Two more values travel alongside
it: the **user** and the **password**. Together, the URL plus user plus password are the full
address your program dials to reach the database.

### Why it is needed

To open a connection, Java must know six things: that this is a JDBC address, which kind of
database, which machine, which port, which database inside the server, and who you are. The URL
packs the location into one string; the user and password are handed over next to it. Get one
part wrong and the connection fails — but the error message names the part that was wrong.

### Key points

- Shape: `jdbc:postgresql://host:port/database`, plus a separate user and password.
- `jdbc:` marks it as a JDBC address; `postgresql:` chooses the driver (the subprotocol).
- `localhost` is this machine; `5432` is Postgres's default port; the last part is the database.
- "Refused" = never reached the server (check host/port). `FATAL: ...` = reached and rejected
  (check database name or credentials).

### How it works, step by step

```text
jdbc : postgresql :// localhost : 5432 / zenith
 |         |            |          |       |
 |         |            |          |       └─ database: which database inside the server
 |         |            |          └───────── port: which door on the machine (Postgres uses 5432)
 |         |            └──────────────────── host: which machine (localhost = this machine)
 |         └───────────────────────────────── subprotocol: which driver to use (the Postgres one)
 └─────────────────────────────────────────── this is a JDBC address
```

1. `jdbc:` tells Java this is a JDBC address.
2. `postgresql:` is the **subprotocol**; Java hands the address to the driver that recognises it.
3. `localhost` is the **host** — the machine to dial (`localhost` = this machine).
4. `5432` is the **port** — the numbered door Postgres listens on.
5. `zenith` is the **database** to open inside the server.
6. The **user** and **password** are passed separately when connecting.
7. Order of use: pick driver → dial host:port → check user/password → open database. Each stage
   has its own failure.

### Example

One program, four attempts; each changes exactly one part of the address.

```java
import java.sql.Connection;
import java.sql.DriverManager;

public class JdbcUrlDemo {
    public static void main(String[] args) {
        attempt("Correct address + credentials",
                "jdbc:postgresql://localhost:5432/zenith", "postgres", "dev");
        attempt("Wrong database name",
                "jdbc:postgresql://localhost:5432/nosuchdb", "postgres", "dev");
        attempt("Wrong port (9999, nothing listening)",
                "jdbc:postgresql://localhost:9999/zenith", "postgres", "dev");
        attempt("Wrong password",
                "jdbc:postgresql://localhost:5432/zenith", "postgres", "wrongpass");
    }

    static void attempt(String label, String url, String user, String password) {
        try {
            Connection c = DriverManager.getConnection(url, user, password);
            System.out.println(label + " -> CONNECTED, current database = " + c.getCatalog());
            c.close();
        } catch (Exception e) {
            System.out.println(label + " -> FAILED: " + e.getMessage());
        }
    }
}
```

Real output against the `zenith` database:

```text
Correct address + credentials -> CONNECTED, current database = zenith
Wrong database name -> FAILED: FATAL: database "nosuchdb" does not exist
Wrong port (9999, nothing listening) -> FAILED: Connection to localhost:9999 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.
Wrong password -> FAILED: FATAL: password authentication failed for user "postgres"
```

### Walkthrough

1. **Correct address:** connects; `c.getCatalog()` asks the connection which database it is in and
   answers `zenith`, confirming the `/zenith` part worked.
2. **Wrong database:** machine, port, and password fine, but no `nosuchdb` exists; the server
   replies `FATAL: database "nosuchdb" does not exist`. `FATAL` = the message came from Postgres,
   so it was truly reached.
3. **Wrong port:** nothing listens on 9999; the dial never reaches Postgres →
   `Connection to localhost:9999 refused`. The classic "refused": check host and port.
4. **Wrong password:** server reached and said no → `FATAL: password authentication failed`.
   Address/port fine; only credentials wrong.

Pattern: "refused" = you never arrived (check machine/port). `FATAL ...` = you arrived and were
pushed back (check database name or credentials).

### Important terms

| Term | Meaning |
| --- | --- |
| **JDBC URL** | `jdbc:postgresql://host:port/database` — where the database is and which one. |
| **Subprotocol** | The `postgresql:` part that selects which driver handles the address. |
| **Host / port** | Which machine / which numbered door to dial. |
| **`getCatalog()`** | Asks an open connection which database it is currently in. |
| **`FATAL`** | Prefix on messages written by Postgres itself — proof the server was reached. |

### Common mistakes

- **Dropping `jdbc:`.** `postgresql://localhost:5432/zenith` is the `psql` form, not JDBC. JDBC
  needs `jdbc:postgresql://...`, or you get `No suitable driver found`.
- **Confusing "refused" with "authentication failed".** Refused = machine/port; authentication
  failed = credentials. Fixing the password for a "refused" wastes time.
- **Thinking `localhost` means the container.** `localhost` is your own machine; the container's
  port is published to your machine's 5432.
- **Port mismatch.** The URL's port must equal the port the server actually listens on.

### Where it is used

Every Java program that talks to a database sets a JDBC URL, user, and password. In raw JDBC you
pass them to `DriverManager.getConnection`. In Spring Boot you write them as
`spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password` (subtask 1).
Cloud providers hand you a ready-made JDBC URL.

### Do it yourself — see each part of the address fail (blank laptop)

**Before you start (one time) — make sure Postgres is running and seeded.**

```bash
# 1. Start Postgres (skip if the pg container already runs)
docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 \
  -v pgdata:/var/lib/postgresql/data postgres:17-alpine

# 2. Create the zenith database (skip if it already exists)
docker exec pg psql -U postgres -c "CREATE DATABASE zenith;"

# 3. Load the tables and sample rows (db-setup.sql from this task folder)
docker exec -i pg psql -U postgres -d zenith < db-setup.sql

# 4. Check
docker exec pg psql -U postgres -d zenith -c "SELECT count(*) FROM event;"
```

Step 4 must print `7`. If step 1 says the name `pg` is already in use, the container exists —
run `docker start pg` instead, or just continue.

**Then run the demo.**

1. Create `JdbcUrlDemo.java` with the code from the Example above.
2. Compile (driver on the classpath): `javac -cp .:postgresql-42.7.4.jar JdbcUrlDemo.java`
   (Windows: `javac -cp ".;postgresql-42.7.4.jar" JdbcUrlDemo.java`).
3. Run: `java -cp .:postgresql-42.7.4.jar JdbcUrlDemo`.

Expected: `CONNECTED, current database = zenith` for the first attempt, then three distinct
failures — `database "nosuchdb" does not exist`, `Connection to localhost:9999 refused`, and
`password authentication failed for user "postgres"`.

**Done when:** you can point at each failure line and say which part of the address caused it.

### Summary for revision

- The address is `jdbc:postgresql://host:port/database` plus a user and password.
- `jdbc:` marks JDBC; `postgresql:` picks the driver; then host, port, database.
- "Refused" = never reached the server (host/port). `FATAL ...` = reached and rejected
  (database name or credentials).
- Spring writes the same three values as `spring.datasource.url`/`username`/`password`.

### Check questions, with answers

**Question 1.** Your program connects with `jdbc:postgresql://localhost:5432/zenith`, user
`postgres`, password `dev`, and fails with `Connection to localhost:5432 refused. Check that the
hostname and port are correct ...`. You are certain `dev` is correct. Is the password the likely
problem? Name two realistic causes, and the first thing to check.

**Answer.** The password is not the problem: "refused" means nothing answered at
`localhost:5432`, so Postgres never even saw the password. Two realistic causes: (1) the Postgres
container is not running (stopped, crashed, or never started); (2) the port is wrong — Postgres
is listening elsewhere, or the container's port is not published to 5432. (A third: the server is
still booting.) First check: is the database actually running on that published port — e.g.,
`docker ps` shows `pg` as `Up` with `0.0.0.0:5432->5432`.

**Question 2.** Address `jdbc:postgresql://localhost:5432/zenith`, user `postgres`, password
`dev`. (a) Say what each piece is: `jdbc:`, `postgresql:`, `localhost`, `5432`, `zenith`.
(b) You change only `zenith` to `nosuchdb` and keep everything else correct; it prints
`FATAL: database "nosuchdb" does not exist`. Does that prove the server was reached? Why?

**Answer.** (a) `jdbc:` = this is a JDBC address; `postgresql:` = the subprotocol that selects the
Postgres driver; `localhost` = the host, this machine; `5432` = the port Postgres listens on;
`zenith` = the database to open. (b) Yes. The message is prefixed `FATAL`, which Postgres itself
writes. To report "database nosuchdb does not exist", the server had to be reached, the port had
to answer, and the credentials had to pass — only the database name was wrong.

### Your doubts

None recorded for this lesson.

---

## Lesson 4 — `Connection` and `DriverManager` (one open conversation)

### Definition

A **`Connection`** is one open, live conversation with the database.
**`DriverManager.getConnection(url, user, password)`** is the call that opens one and hands it to
you. Think of a `Connection` as a phone call that is currently connected: while live you can
talk, and when you hang up it is over.

### Why it is needed

Everything you do with a database happens through a connection — sending a query needs one,
reading results needs one. So the first step of any database work is to open a connection. A
connection is also a real, live resource: it holds an open network link and makes the server set
aside memory for your session. When you are done you must close it, to hand those resources back.

### Key points

- `DriverManager.getConnection(url, user, password)` returns a real `Connection`.
- Opening is not free: it is a network round trip plus a login.
- A `Connection` can be inspected while open (database name, user, product version).
- Once `close()` is called, the connection is dead — using it throws
  `This connection has been closed`.

### How it works, step by step

1. You call `DriverManager.getConnection(url, user, password)`.
2. `DriverManager` picks the registered driver that handles the URL and asks it to open a
   connection.
3. The driver dials host and port, logs in, opens the named database, and returns a real
   `Connection` object (`org.postgresql.jdbc.PgConnection`).
4. You use the connection to run queries.
5. You call `connection.close()` to end the conversation and free the resources.

### Example

```java
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;

public class ConnectionDemo {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/zenith";

        long start = System.nanoTime();
        Connection c = DriverManager.getConnection(url, "postgres", "dev");
        long openMillis = (System.nanoTime() - start) / 1_000_000;

        System.out.println("Opening the connection took about " + openMillis + " ms");
        System.out.println("Real class behind the Connection: " + c.getClass().getName());
        System.out.println("Is it open right now? " + !c.isClosed());

        DatabaseMetaData meta = c.getMetaData();
        System.out.println("Talking to: " + meta.getDatabaseProductName()
                + " " + meta.getDatabaseProductVersion());
        System.out.println("Connected as user: " + meta.getUserName());
        System.out.println("Current database: " + c.getCatalog());

        c.close();
        System.out.println("After close(), is it closed? " + c.isClosed());

        try {
            c.getCatalog();
        } catch (Exception e) {
            System.out.println("Using it after close -> "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
```

Real output (the millisecond number varies):

```text
Opening the connection took about 195 ms
Real class behind the Connection: org.postgresql.jdbc.PgConnection
Is it open right now? true
Talking to: PostgreSQL 17.11
Connected as user: postgres
Current database: zenith
After close(), is it closed? true
Using it after close -> PSQLException: This connection has been closed.
```

### Walkthrough

1. **~195 ms to open.** Part is one-time startup (loading driver classes). But even warmed up,
   opening is a network round trip plus a login — not free. This is the reason pools exist.
2. **Real class `PgConnection`.** The variable's type is the interface `Connection`; the real
   object is the driver's class.
3. **Open right now? true.** `isClosed()` is false while live.
4. **PostgreSQL 17.11, user postgres, database zenith.** A connection knows who it logged in as
   and which database it opened.
5. **After close(), closed? true.** `close()` hung up.
6. **Using after close → `This connection has been closed`.** A closed connection is dead; open a
   new one.

### Important terms

| Term | Meaning |
| --- | --- |
| **`Connection`** | One open, live conversation with the database. |
| **`DriverManager.getConnection`** | Opens a new connection and returns it. |
| **`isClosed()`** | Whether the connection has been closed. |
| **`DatabaseMetaData`** | Facts about the connection: product, version, user. |
| **Resource** | Something live that must be closed to be returned (a connection is one). |

### Common mistakes

- **Not closing the connection.** It keeps a network link and server memory; many unclosed
  connections exhaust the server. Always close.
- **Using a connection after closing it.** Throws `This connection has been closed`.
- **Opening a new connection for every tiny query in a loop.** Each open pays the full cost;
  thousands of opens crawl. Pools fix this.
- **Assuming `getConnection` is instant.** It is a network and login round trip.

### Where it is used

Every JDBC operation begins by getting a `Connection`. Small scripts open one, work, and close.
Web apps borrow an already-open connection from a pool per request instead of paying the open
cost each time.

### Do it yourself — open, inspect, and close a connection (blank laptop)

With Postgres running and seeded (see Lesson 3's "Before you start"), and the driver jar present:

1. Create `ConnectionDemo.java` with the code from the Example above.
2. Compile: `javac -cp .:postgresql-42.7.4.jar ConnectionDemo.java`
   (Windows: `javac -cp ".;postgresql-42.7.4.jar" ConnectionDemo.java`).
3. Run: `java -cp .:postgresql-42.7.4.jar ConnectionDemo`.

Expected: lines reporting the open time, `PgConnection`, `PostgreSQL 17.x`, user `postgres`,
database `zenith`, `closed? true`, and finally `This connection has been closed.`

**Done when:** the program connects, prints the database product and name, and then proves the
connection is dead after `close()`.

### Summary for revision

- A `Connection` is one live conversation; `DriverManager.getConnection` opens it.
- Opening costs a network round trip plus a login — not free.
- A connection knows its database, user, and server version while open.
- After `close()` it is dead; reuse throws `This connection has been closed`.

### Check questions, with answers

**Question 1.** A program opens a connection, closes it with `c.close()`, then calls
`c.getCatalog()` on the same variable and fails with
`PSQLException: This connection has been closed.` Why does this fail, given what a `Connection`
actually is?

**Answer.** A `Connection` is one open, live conversation — an open network link plus server-side
session memory. `close()` ends that conversation and frees those resources, so there is no longer
a live link to talk over. `getCatalog()` needs to use the connection, but it is dead, so it throws
`This connection has been closed`. The only remedy is to open a new connection.

**Question 2.** In the demo, opening one connection took about 195 ms. A web app, for every
request, opens a brand-new connection, runs one query that takes 2 ms, then closes the
connection. What is the problem, and what mechanism (covered later) fixes it?

**Answer.** Almost all the time and work go into opening and closing the connection, not into the
2 ms query — a network round trip plus a login per request, repeated endlessly. It is slow and
wasteful, and under load it hammers the server with constant connect/disconnect. The fix is a
**connection pool**: a set of connections kept open and reused, so each request borrows a ready
connection and returns it, paying the open cost once instead of per request.

### Your doubts

None recorded for this lesson.

---

## Lesson 5 — `PreparedStatement` and the `?` placeholder (a query with holes)

### Definition

A **`PreparedStatement`** is a SQL statement you send to the database with `?` marks where values
will go. Each `?` is a **placeholder** — a hole left in the SQL on purpose. You create one with
`connection.prepareStatement(sql)`. Picture a paper form with a blank: "Find events in city
____." The sentence is fixed; the blank is filled in later. The `?` is that blank.

### Why it is needed

Two reasons. First, you run the same shape of query many times with different values (events in
Hyderabad today, Mumbai tomorrow); a placeholder lets you write the SQL once and plug in a
different value each time. Second, and deeper: the SQL text and the values stay **separate**. The
database reads the SQL structure first, with the holes empty; the values arrive later through a
different door and are never pasted into the SQL text. That separation is the key to safety
(full security lesson later). For now: with a `?`, the value is not part of the SQL string.

### Key points

- `connection.prepareStatement(sql)` returns a `PreparedStatement` for SQL that contains `?`.
- A `?` can only stand for a **value**, never a column name or table name.
- Holes are **positional**: first `?` is parameter 1, second `?` is parameter 2.
- The values are not in the SQL text; they are supplied separately (next lesson).

### How it works, step by step

1. Write SQL with a `?` wherever a value belongs: `... WHERE venue_id = ? AND name = ?`.
2. Call `connection.prepareStatement(sql)`, handing the SQL (holes and all) to the database.
3. The database reads the statement and plans it now, while the holes are empty; it knows how many
   holes there are.
4. Later, you fill each hole with a value, then run the statement.
5. Holes are counted left to right, starting at 1.

### Example

```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;

public class PreparedStatementDemo {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/zenith";
        String sql = "SELECT id, name FROM event WHERE venue_id = ? AND name = ?";

        Connection c = DriverManager.getConnection(url, "postgres", "dev");
        PreparedStatement ps = c.prepareStatement(sql);

        System.out.println("SQL text sent to the database:");
        System.out.println("  " + sql);

        ParameterMetaData pmd = ps.getParameterMetaData();
        System.out.println("Number of ? holes this statement expects: " + pmd.getParameterCount());
        System.out.println("No values are in that SQL text. They get supplied separately, later.");

        ps.close();
        c.close();
    }
}
```

Real output:

```text
SQL text sent to the database:
  SELECT id, name FROM event WHERE venue_id = ? AND name = ?
No values are in that SQL text. They get supplied separately, later.
Number of ? holes this statement expects: 2
```

### Walkthrough

1. `sql` contains two `?` and no values of any kind — just the two holes.
2. `c.prepareStatement(sql)` sends that SQL to be prepared; you get a `PreparedStatement` standing
   for "this query, with two holes to fill".
3. `ps.getParameterMetaData().getParameterCount()` asks "how many holes?" → `2`.
4. The printed SQL still shows the two `?`. The values are not there yet. That is the separation.

### Important terms

| Term | Meaning |
| --- | --- |
| **`PreparedStatement`** | A statement sent with `?` placeholders; values supplied separately. |
| **Placeholder (`?`)** | A hole in the SQL standing for a value, never for structure. |
| **`prepareStatement(sql)`** | Creates a `PreparedStatement` from SQL that contains `?`. |
| **Positional** | Holes are identified by position (1, 2, 3), not by name, in raw JDBC. |

### Common mistakes

- **Gluing the value into the SQL.** `"... WHERE city = '" + city + "'"` puts the value inside the
  SQL string — the dangerous habit this task cures. Use a `?`.
- **Thinking `?` has a name.** In raw JDBC it is found by position. (Spring's `JdbcClient` uses
  named holes like `:city`.)
- **Using `?` for a column or table name.** A `?` can only be a value; `SELECT ? FROM event` or
  `ORDER BY ?` for a column name does not work.

### Where it is used

Any query with a value that changes: `WHERE city = ?`, `WHERE id = ?`, `VALUES (?, ?, ?)`. The
rule: every value from outside the query goes through a `?`, never glued into the SQL text.

### Link to the task

The raw-JDBC query in subtask 2 ends with `WHERE v.city = ?`. That `?` is this lesson. The whole
security point of subtasks 4 and 5 is the difference between this `?` and a value glued into the
string — you are building the safe habit now.

### Do it yourself — prepare a statement and count its holes (blank laptop)

With Postgres running and seeded (Lesson 3's "Before you start") and the driver jar present:

1. Create `PreparedStatementDemo.java` with the code from the Example above.
2. Compile: `javac -cp .:postgresql-42.7.4.jar PreparedStatementDemo.java`.
3. Run: `java -cp .:postgresql-42.7.4.jar PreparedStatementDemo`.

Expected: the SQL printed with two `?`, and `Number of ? holes this statement expects: 2`.

**Done when:** you see the hole count is `2`, and you can point out there are no values in the SQL.

### Summary for revision

- A `PreparedStatement` is SQL with `?` holes; values are supplied separately.
- `?` stands only for a value, never a column or table name.
- Holes are positional, starting at 1.
- The value is never part of the SQL text — the root of the later security win.

### Check questions, with answers

**Question 1.** For `String sql = "SELECT id, name FROM event WHERE venue_id = ? AND starts_at > ?";`
(a) how many placeholders does it have? (b) What can each `?` stand for — a value, or a column or
table name?

**Answer.** (a) Two placeholders. (b) Each `?` can stand only for a **value**: one value for
`venue_id` and one value for `starts_at`. Neither can be a column or table name, because those are
part of the SQL structure, not values.

**Question 2.** Compare, where `city` is a Java variable holding user text:

```java
String sqlA = "SELECT id, name FROM event WHERE city = " + city;   // Way A
String sqlB = "SELECT id, name FROM event WHERE city = ?";          // Way B
```

In Way A, is the city value part of the SQL text or separate? In Way B?

**Answer.** Way A pastes the city value into the SQL text — it becomes part of the SQL string
itself. Way B keeps the SQL as just a hole (`?`); the city value is not in the SQL text and is
supplied separately later. Way A is the dangerous habit; why it is dangerous is a later lesson.

### Your doubts

None recorded for this lesson.

---

## Lesson 6 — Bind parameters (filling the `?` holes)

### Definition

A **bind parameter** is a value you supply to fill a `?` hole. You set it with a method named for
the value's type: `setString` for text, `setInt` for a whole number, and so on. The hole is chosen
by its **position**, counting from 1. So `ps.setString(1, city)` fills the first `?` with the text
in `city`. "Bind" means "attach this value to that hole".

### Why it is needed

A `?` is only a hole; to run the query every hole needs a value, and binding puts the value in.
Deeper: when you bind a value, the database treats it strictly as **data** — never as SQL. So
punctuation inside the value, even quotes, is just characters. This is the property that later
defeats attacks (full story in Lessons 10–11).

### Key points

- Fill each hole with a setter: `ps.setString(1, "Hyderabad")`.
- Positions are 1-based and count left to right.
- Match the setter to the type: `setString`, `setInt`, `setLong`, `setBigDecimal`, `setObject`.
- A bound value is always data, never SQL — even quotes are just characters.
- Run with a hole unset → `No value specified for parameter N`.

### How it works, step by step

1. After `prepareStatement`, call one setter per hole: `ps.setString(1, "Hyderabad")`.
2. The number is the position, starting at 1.
3. Pick the setter matching the value's type (`setString` for text, `setObject` for a `uuid`, …).
4. The value travels separately from the SQL; the database slots it into the prepared plan as data.
5. Reuse the same statement: set a new value and run again.
6. An unset hole at run time throws an error naming the hole.

### Example

```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class BindParamsDemo {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/zenith";
        String sql = "SELECT e.name FROM event e JOIN venue v ON v.id = e.venue_id "
                   + "WHERE v.city = ? ORDER BY e.name";

        Connection c = DriverManager.getConnection(url, "postgres", "dev");
        PreparedStatement ps = c.prepareStatement(sql);

        try {                                   // 1) run without filling the hole
            ps.executeQuery();
        } catch (Exception e) {
            System.out.println("Ran without binding -> " + e.getMessage());
        }

        ps.setString(1, "Hyderabad");           // 2) fill hole 1
        System.out.println("\nBound parameter 1 = \"Hyderabad\":");
        printNames(ps.executeQuery());

        ps.setString(1, "Mumbai");              // 3) reuse the same statement
        System.out.println("\nBound parameter 1 = \"Mumbai\":");
        printNames(ps.executeQuery());

        ps.setString(1, "Hyderabad' OR '1'='1"); // 4) quote-filled value = plain data
        System.out.println("\nBound parameter 1 = \"Hyderabad' OR '1'='1\"  (note the quotes):");
        printNames(ps.executeQuery());

        ps.close();
        c.close();
    }

    static void printNames(ResultSet rs) throws Exception {
        int count = 0;
        while (rs.next()) {
            System.out.println("  - " + rs.getString("name"));
            count++;
        }
        System.out.println("  (" + count + " rows)");
        rs.close();
    }
}
```

Real output:

```text
Ran without binding -> No value specified for parameter 1.

Bound parameter 1 = "Hyderabad":
  - Classical Evening
  - Indie Fest
  - Monsoon Rock Night
  - Sufi Night
  (4 rows)

Bound parameter 1 = "Mumbai":
  - Blues Brunch
  - Jazz at the Note
  - Open Mic Night
  (3 rows)

Bound parameter 1 = "Hyderabad' OR '1'='1"  (note the quotes):
  (0 rows)
```

### Walkthrough

1. **No binding → error.** The hole was empty, so running fails with
   `No value specified for parameter 1`.
2. **Bind "Hyderabad".** `setString(1, "Hyderabad")` fills hole 1; the query returns the four
   Hyderabad events.
3. **Reuse for "Mumbai".** The same statement, hole 1 set to "Mumbai", returns the three Mumbai
   events. One prepared statement, many values.
4. **Quote-filled value → 0 rows.** The whole string `Hyderabad' OR '1'='1` was treated as one
   literal city name. No such city exists, so nothing matched. The quotes were data, not SQL.

### Important terms

| Term | Meaning |
| --- | --- |
| **Bind parameter** | A value supplied to fill a `?` hole; treated strictly as data. |
| **`setString(pos, value)`** | Fills hole number `pos` (1-based) with text. |
| **Position** | Which `?` to fill, counting left to right from 1. |
| **Reuse** | Setting new values on the same `PreparedStatement` and running again. |

### Common mistakes

- **Counting from 0.** `setString(0, ...)` fails; positions start at 1.
- **Forgetting a hole.** Running with an unset `?` throws `No value specified for parameter N`.
- **Adding your own quotes.** `setString(1, "'Hyderabad'")` makes the quotes part of the data and
  matches nothing. Bind the raw value.
- **Wrong setter for the type.** Use `setInt` for `integer`, `setBigDecimal` for money, etc.

### Where it is used

Every query with a value. Match the setter to the column type: `setString` (text), `setInt`
(`integer`), `setLong` (`bigint`), `setBigDecimal` (`numeric(19,4)` money), `setObject(1, uuid)`
(`uuid`). Position numbers follow the order of the `?` holes.

### Link to the task

The raw-JDBC code in subtask 2 contains `ps.setString(1, city);` — this whole lesson in one line.
The injection fix in subtask 5 is exactly step 4 above: pass the malicious input through
`setString` and it matches nothing. Lesson 11 explains *why* at the database level.

### Do it yourself — fill, reuse, and test a hole (blank laptop)

With Postgres running and seeded (Lesson 3's "Before you start") and the driver jar present:

1. Create `BindParamsDemo.java` with the code from the Example above.
2. Compile: `javac -cp .:postgresql-42.7.4.jar BindParamsDemo.java`.
3. Run: `java -cp .:postgresql-42.7.4.jar BindParamsDemo`.

Expected: the "no value specified" error, then 4 Hyderabad events, then 3 Mumbai events, then
0 rows for the quote-filled value.

**Done when:** you see one statement reused with different values, and the quote-filled value
returning 0 rows.

### Summary for revision

- Binding fills each `?` with a value, by 1-based position.
- A bound value is always data — quotes inside it are just characters.
- Match the setter to the type; reuse the statement with new values.
- An unset hole at run time throws `No value specified for parameter N`.

### Check questions, with answers

**Question 1.** You have
`PreparedStatement ps = c.prepareStatement("SELECT name FROM venue WHERE city = ? AND capacity > ?");`.
Write the two lines that make the query mean "venues in Mumbai with capacity greater than 300".
For each line, say which `?` it fills and why that position number.

**Answer.**

```java
ps.setString(1, "Mumbai");  // fills the first ? (city); position 1 = first hole, left to right
ps.setInt(2, 300);          // fills the second ? (capacity); position 2 = second hole
```

Positions count left to right from 1, so `1` is the city hole and `2` is the capacity hole.
`setInt` is used for `capacity` because it is an `integer` column.

**Question 2.** In the demo, binding parameter 1 to `Hyderabad' OR '1'='1` returned 0 rows. Did
the database run the `OR '1'='1` part as query logic, or treat the whole thing as data? Why
exactly 0 rows?

**Answer.** It treated the entire string as one piece of data — a single city name to match — and
did not run `OR '1'='1` as logic. The quotes were just characters in the value. The result was 0
rows because no venue has a city literally named `Hyderabad' OR '1'='1`. A bound value can never
become SQL, so it can only match, or fail to match, as data.

### Your doubts

None recorded for this lesson.

---

## Lesson 7 — The `ResultSet` (run the query, walk the rows)

### Definition

A **`ResultSet`** is a cursor over the rows a query returned. A **cursor** is a pointer that sits
on one row at a time. `ps.executeQuery()` runs a `SELECT` and hands you back a `ResultSet`; you
then step the cursor forward, row by row, reading each row as you go.

### Why it is needed

A query can return many rows. Java does not get them all as one lump; the database sends them, and
the `ResultSet` lets you walk through them one at a time, reading the columns you want from each
row. This is how query results come back into your program.

### Key points

- `ps.executeQuery()` returns a `ResultSet`; the cursor starts **before** the first row.
- `rs.next()` moves one row forward; returns `true` if on a row, `false` when none remain.
- Standard shape: `while (rs.next()) { ...read this row... }`.
- Read by name (`rs.getString("name")`) or by 1-based index following the `SELECT` list order.
- Use a getter matching the type (`getString`, `getInt`, `getBigDecimal`, `getObject(..., UUID.class)`).

### How it works, step by step

1. `ResultSet rs = ps.executeQuery();` runs the `SELECT` and returns the cursor.
2. The cursor starts before the first row — not on any row yet.
3. `rs.next()` moves forward one row; `true` = landed on a row, `false` = no more rows.
4. Loop: `while (rs.next()) { ... }` — body runs once per row.
5. Inside, read columns by name or by 1-based index, with a type-appropriate getter.

### Example

```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class ResultSetDemo {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/zenith";
        String sql = "SELECT e.id, e.name FROM event e JOIN venue v ON v.id = e.venue_id "
                   + "WHERE v.city = ? ORDER BY e.name";

        Connection c = DriverManager.getConnection(url, "postgres", "dev");
        PreparedStatement ps = c.prepareStatement(sql);
        ps.setString(1, "Hyderabad");

        ResultSet rs = ps.executeQuery();
        System.out.println("A ResultSet starts positioned BEFORE the first row.");

        int rowNumber = 0;
        while (rs.next()) {                               // step to next row; false when none left
            rowNumber++;
            UUID id = rs.getObject("id", UUID.class);     // by NAME, typed as UUID
            String nameByName = rs.getString("name");     // by NAME
            String nameByIndex = rs.getString(2);         // same column by INDEX (1-based)
            System.out.println("Row " + rowNumber + ": id=" + id
                    + "  name(byName)=" + nameByName + "  name(byIndex)=" + nameByIndex);
        }
        System.out.println("rs.next() returned false, so the loop ended after "
                + rowNumber + " rows.");

        rs.close();
        ps.close();
        c.close();
    }
}
```

Real output:

```text
A ResultSet starts positioned BEFORE the first row.
Row 1: id=aaaaaaaa-0000-0000-0000-000000000002  name(byName)=Classical Evening  name(byIndex)=Classical Evening
Row 2: id=aaaaaaaa-0000-0000-0000-000000000006  name(byName)=Indie Fest  name(byIndex)=Indie Fest
Row 3: id=aaaaaaaa-0000-0000-0000-000000000001  name(byName)=Monsoon Rock Night  name(byIndex)=Monsoon Rock Night
Row 4: id=aaaaaaaa-0000-0000-0000-000000000005  name(byName)=Sufi Night  name(byIndex)=Sufi Night
```

### Walkthrough

1. `executeQuery()` returns `rs` with the cursor before row 1; reading now would fail.
2. First `rs.next()` moves onto row 1 (Classical Evening, since the query sorts by name) and returns
   `true`, so the body runs.
3. `getObject("id", UUID.class)` reads `id` as a Java `UUID`; `getString("name")` reads by name;
   `getString(2)` reads the second `SELECT` column, which is `name` (list is `e.id, e.name`). Both
   name reads match.
4. The loop repeats for rows 2–4.
5. After row 4, `rs.next()` returns `false` (no more rows), so the loop ends; it walked 4 rows.

### Important terms

| Term | Meaning |
| --- | --- |
| **`ResultSet`** | A cursor over the rows a query returned. |
| **Cursor** | A pointer that sits on one row at a time. |
| **`executeQuery()`** | Runs a `SELECT` and returns a `ResultSet`. |
| **`rs.next()`** | Moves to the next row; `true` if on a row, `false` when none remain. |
| **`rs.getString("name")` / `rs.getString(2)`** | Read a column by name / by 1-based index. |

### Common mistakes

- **Reading before the first `next()`.** The cursor starts before row 1; an immediate read throws
  `ResultSet not positioned properly, perhaps you need to call next.` The `while` loop calls it
  for you.
- **Asking for a column not in the `SELECT`.** Throws `The column name X was not found`.
- **Index confusion.** The index is 1-based and follows the `SELECT` list order, not the table's
  column order.
- **NULL trap with numbers.** `getString` returns `null` for SQL `NULL`, but `getInt` returns `0`;
  call `rs.wasNull()` to tell `0` from `NULL`.
- **Leaving the `ResultSet` open.** It holds resources; the next lesson closes it automatically.

### Where it is used

Every `SELECT` in raw JDBC gives a `ResultSet`, read with the same `while (rs.next()) { ... }`
loop. It is the universal shape of reading results by hand; higher tools hide this loop but run it
underneath.

### Link to the task

The raw-JDBC code in subtask 2 reads rows exactly this way:

```java
try (ResultSet rs = ps.executeQuery()) {
    while (rs.next()) {
        System.out.println(rs.getString("id") + " " + rs.getString("name"));
    }
}
```

Two of the manual steps counted in subtask 3 — "step to the next row" and "read each column by
name" — are this lesson's work.

### Do it yourself — walk a ResultSet (blank laptop)

With Postgres running and seeded (Lesson 3's "Before you start") and the driver jar present:

1. Create `ResultSetDemo.java` with the code from the Example above.
2. Compile: `javac -cp .:postgresql-42.7.4.jar ResultSetDemo.java`.
3. Run: `java -cp .:postgresql-42.7.4.jar ResultSetDemo`.

Expected: four rows for Hyderabad, with the by-name and by-index reads matching, and a final line
saying the loop ended after 4 rows.

**Done when:** you see the cursor step through 4 rows and stop when `next()` returns `false`.

### Summary for revision

- `executeQuery()` returns a `ResultSet`; the cursor starts before the first row.
- `rs.next()` steps forward and reports whether a row exists; loop with `while (rs.next())`.
- Read by name, or by 1-based index following the `SELECT` order.
- Match the getter to the type; mind the NULL trap on numbers.

### Check questions, with answers

**Question 1.** A beginner runs `SELECT city, capacity FROM venue`, gets `rs`, and the first line
after `executeQuery()` is `String city = rs.getString("city");` with no `rs.next()` first. What
goes wrong and why? What line must come first?

**Answer.** The cursor starts before the first row, so reading a column before moving onto a row
fails with an error like `ResultSet not positioned properly, perhaps you need to call next.` The
line that must come first is `rs.next()`, normally as the loop condition:
`while (rs.next()) { String city = rs.getString("city"); ... }`, which moves the cursor onto row 1
before the read.

**Question 2.** For `SELECT e.id, e.name FROM event e ...`, the name can be read as
`rs.getString("name")` or `rs.getString(2)`. (a) Why does index `2` refer to the name? (b) If the
query became `SELECT e.name, e.id FROM event e ...`, what would `rs.getString(2)` return?

**Answer.** (a) The column index is 1-based and follows the `SELECT` list order; in `e.id, e.name`,
`id` is column 1 and `name` is column 2, so index `2` is the name. (b) With `e.name, e.id`, column
1 is `name` and column 2 is `id`, so `rs.getString(2)` would return the id. The index follows the
`SELECT` order, not the table's column order.

### Your doubts

None recorded for this lesson.

---

## Lesson 8 — try-with-resources (always close, even on error)

### Definition

**try-with-resources** is a form of `try` that declares resources inside parentheses; Java closes
them for you when the block ends, whether it ends normally or by throwing. A **resource** is
anything that implements `AutoCloseable` (an interface with one method, `close()`). `Connection`,
`PreparedStatement`, and `ResultSet` all implement it.

### Why it is needed

Closing by hand is fragile. Open a connection, run a query, then `c.close()` at the end — if the
query throws, control jumps past the `close()` line and the connection is never closed. That is a
**resource leak**: a resource never returned. One leak is invisible; thousands are fatal, because
the database runs out of connections and everything stops. try-with-resources guarantees `close()`
runs no matter how the block ends.

### Key points

- Syntax: `try (A a = ...; B b = ...) { ... }` — resources declared in the parentheses.
- `close()` runs on both exits: normal completion and exception.
- Resources close in **reverse order** of declaration (last opened, first closed).
- On a thrown exception, resources close first, then the exception propagates.
- Only `AutoCloseable` types may appear in the header.

### How it works, step by step

1. Declare each resource in the parentheses, separated by `;`.
2. Run the body.
3. On leaving the block — normally or by exception — Java calls `close()` on each resource.
4. Closing is reverse order: the last declared is closed first.
5. If the body threw, Java closes the resources, then lets the exception continue.

### Example

A fake `Resource` prints when opened, used, and closed, across a normal block and a throwing
block. Pure Java, no database.

```java
public class TryWithResourcesDemo {
    static class Resource implements AutoCloseable {
        private final String name;
        Resource(String name) { this.name = name; System.out.println("opened " + name); }
        void use()  { System.out.println("using  " + name); }
        public void close() { System.out.println("closed " + name); }
    }

    public static void main(String[] args) {
        System.out.println("=== Normal case: the block finishes ===");
        try (Resource a = new Resource("A (like Connection)");
             Resource b = new Resource("B (like PreparedStatement)")) {
            a.use();
            b.use();
        }

        System.out.println("\n=== Failure case: the body throws halfway ===");
        try {
            try (Resource a = new Resource("A (like Connection)");
                 Resource b = new Resource("B (like PreparedStatement)")) {
                a.use();
                throw new RuntimeException("boom while using resources");
            }
        } catch (Exception e) {
            System.out.println("caught: " + e.getMessage());
        }
    }
}
```

Real output:

```text
=== Normal case: the block finishes ===
opened A (like Connection)
opened B (like PreparedStatement)
using  A (like Connection)
using  B (like PreparedStatement)
closed B (like PreparedStatement)
closed A (like Connection)

=== Failure case: the body throws halfway ===
opened A (like Connection)
opened B (like PreparedStatement)
using  A (like Connection)
closed B (like PreparedStatement)
closed A (like Connection)
caught: boom while using resources
```

### Walkthrough

1. **Normal case:** A then B opened; body uses both; at block end Java closes B then A (reverse).
   No `close()` was written.
2. **Failure case:** A and B opened; body uses A, then throws before using B; Java still closes B
   then A, and only then does the exception reach `catch`. Nothing leaked despite the failure.
3. **Why reverse order matters:** a `ResultSet` lives inside a `PreparedStatement`, which lives on a
   `Connection`. The inner must close before the outer; closing the connection first would strand a
   still-open statement and result set.

### Important terms

| Term | Meaning |
| --- | --- |
| **try-with-resources** | A `try` that auto-closes resources declared in its parentheses. |
| **`AutoCloseable`** | The interface (one method, `close()`) a resource must implement. |
| **Resource leak** | A resource never closed, so never returned; exhausts the pool/server. |
| **Reverse order** | Resources close last-declared-first. |

### Common mistakes

- **Opening the resource inside the body.** Only resources declared in the header are auto-closed.
- **Manual `close()` with no safety.** A plain `try` with `c.close()` at the end leaks whenever the
  body throws first. Use try-with-resources.
- **Expecting declaration order for closing.** Closing is reverse order (which is what you want).
- **A non-`AutoCloseable` type in the header.** Not allowed there.

### Where it is used

Every closeable resource: database connections, statements, result sets, files, sockets, streams.
It is the standard modern way to handle all of them.

### Link to the task

The raw-JDBC code in subtask 2 uses nested try-with-resources:

```java
try (Connection c = DriverManager.getConnection(url, user, password);
     PreparedStatement ps = c.prepareStatement(sql)) {
    ps.setString(1, city);
    try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
            System.out.println(rs.getString("id") + " " + rs.getString("name"));
        }
    }
}
```

This closes `ResultSet`, then `PreparedStatement`, then `Connection`, even on error. Subtask 6
(leak connections) is exactly what happens when you remove this construct.

### Do it yourself — see auto-close and reverse order (blank laptop)

Pure Java; no database or driver needed.

1. Create `TryWithResourcesDemo.java` with the code from the Example above.
2. Compile: `javac TryWithResourcesDemo.java`.
3. Run: `java TryWithResourcesDemo`.

Expected: in the normal case, `closed B` before `closed A`; in the failure case, both still closed
(B then A) before `caught: boom while using resources`.

**Done when:** you see resources closed in reverse order, and closed even when the body throws.

### Summary for revision

- try-with-resources declares resources in parentheses and auto-closes them on every exit.
- Close order is reverse of declaration — the safe order for `ResultSet`/`Statement`/`Connection`.
- It runs `close()` even when the body throws, preventing leaks.
- Removing it is what creates the connection-leak disaster (subtask 6).

### Check questions, with answers

**Question 1.** In the demo's normal case, `A` is declared first and `B` second, yet the output
shows `closed B` before `closed A`. (a) Why? (b) Give a concrete JDBC reason this order is safe,
using `Connection`, `PreparedStatement`, and `ResultSet`.

**Answer.** (a) try-with-resources closes resources in reverse order of declaration — last opened,
first closed — so `B` (declared second) closes before `A`. (b) A `ResultSet` is created from a
`PreparedStatement`, which is created from a `Connection`; the inner ones depend on the outer ones.
Closing must go `ResultSet` → `PreparedStatement` → `Connection`. If the `Connection` closed first,
the still-open statement and result set would lose the connection they depend on. Reverse order
produces exactly the safe sequence.

**Question 2.** A program opens a `Connection`, then a query throws before control reaches a
hand-written `c.close()`. (a) With manual closing only (no try-with-resources, no `finally`), is the
connection closed? (b) With try-with-resources, is it closed? Explain each in one sentence.

**Answer.** (a) No — the exception jumps past the `c.close()` line, so it never runs and the
connection leaks. (b) Yes — try-with-resources runs `close()` as the block exits, including when it
exits via an exception, so the connection is closed and nothing leaks.

### Your doubts

None recorded for this lesson.

---

## Lesson 9 — The full raw-JDBC program, and counting the manual steps

### Definition

This lesson assembles the previous lessons into one complete program that reads rows from Postgres
by hand (subtask 2), then counts every manual step it forced on you (subtask 3).

### Why it is needed

You learned each piece alone; now you see them combine into a working whole. Counting the steps
shows how much JDBC makes you do by hand — which is exactly what every higher tool later removes.
You cannot appreciate what they remove until you have done all of it once.

### Key points

- The program: set address + city, write SQL with `?`, open + prepare + bind + execute + step +
  read + close.
- The city is on `venue`, so the query joins `event` to `venue` and filters `v.city = ?`.
- try-with-resources closes `ResultSet`, `PreparedStatement`, `Connection` in the right order.
- Seven manual steps for one query; higher tools collapse most of them.

### How it works, step by step

1. Set `url`, `user`, `password`, and the `city` to search for.
2. Write SQL joining `event` to `venue`, ending in `WHERE v.city = ?`.
3. Open the connection and prepare the statement in a try-with-resources header.
4. Bind the city into the hole.
5. Execute inside a nested try-with-resources so the `ResultSet` also auto-closes.
6. Loop with `while (rs.next())` and read each column by name.
7. On block exit, the three resources close in reverse order automatically.

### Example

```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class RawJdbc {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/zenith";
        String user = "postgres";
        String password = "dev";
        String city = args.length > 0 ? args[0] : "Hyderabad";

        String sql = "SELECT e.id, e.name FROM event e "
                   + "JOIN venue v ON v.id = e.venue_id "
                   + "WHERE v.city = ?";

        try (Connection c = DriverManager.getConnection(url, user, password);  // 1. open connection
             PreparedStatement ps = c.prepareStatement(sql)) {                 // 2. prepare statement
            ps.setString(1, city);                                             // 3. bind by position
            try (ResultSet rs = ps.executeQuery()) {                          // 4. execute
                System.out.println("Events in " + city + ":");
                while (rs.next()) {                                           // 5. step row by row
                    String id = rs.getString("id");                          // 6. read by column name
                    String name = rs.getString("name");                      // 6. read by column name
                    System.out.println("  " + id + "  " + name);
                }
            }                                                                 // 7. close rs
        }                                                                     // 7. close ps, then c
    }
}
```

Real output:

```text
### Run with default city (Hyderabad):
Events in Hyderabad:
  aaaaaaaa-0000-0000-0000-000000000006  Indie Fest
  aaaaaaaa-0000-0000-0000-000000000005  Sufi Night
  aaaaaaaa-0000-0000-0000-000000000002  Classical Evening
  aaaaaaaa-0000-0000-0000-000000000001  Monsoon Rock Night

### Run with city argument Mumbai:
Events in Mumbai:
  aaaaaaaa-0000-0000-0000-000000000007  Blues Brunch
  aaaaaaaa-0000-0000-0000-000000000004  Open Mic Night
  aaaaaaaa-0000-0000-0000-000000000003  Jazz at the Note
```

### Walkthrough

- The SQL joins `event` to `venue` to filter by `venue.city`, ending in `WHERE v.city = ?`.
- Step 1 opens the connection; step 2 prepares the statement (both in the header).
- Step 3 binds the city; step 4 executes and returns the `ResultSet` (nested header).
- Step 5 walks the rows; step 6 reads `id` and `name` by name.
- Step 7: the inner block closes `rs`; the outer closes `ps` then `c`, in reverse order.
- Four events for Hyderabad, three for Mumbai. No `ORDER BY`, so row order is not guaranteed; add
  `ORDER BY e.name` for a fixed order.

### Counting the manual steps (subtask 3)

| # | Manual step | The code | Removed later by |
| --- | --- | --- | --- |
| 1 | Open the connection | `DriverManager.getConnection(...)` | a connection pool hands you one |
| 2 | Prepare the statement | `c.prepareStatement(sql)` | `JdbcClient.sql(...)` |
| 3 | Bind the value by position | `ps.setString(1, city)` | `.param("city", city)` by name |
| 4 | Execute | `ps.executeQuery()` | `.query(...)` |
| 5 | Step through rows | `while (rs.next())` | `.list()` returns a `List` |
| 6 | Read each column by name | `rs.getString("id")`, `...("name")` | a row mapper builds the object |
| 7 | Close three resources in order | the two try-with-resources blocks | handled for you |

Seven manual steps for one simple query. `JdbcClient` later collapses most of them — but you still
write the SQL yourself.

### Important terms

| Term | Meaning |
| --- | --- |
| **Raw JDBC** | Using the JDBC interfaces directly, by hand, with no helper layer. |
| **Join** | Combining rows from two tables on a matching condition (`v.id = e.venue_id`). |
| **Manual step** | One thing JDBC requires you to do explicitly (open, prepare, bind, …). |

### Common mistakes

- **No driver on the classpath** → `No suitable driver found for jdbc:postgresql://...`.
- **Forgetting to bind** → `No value specified for parameter 1`.
- **Reading before `rs.next()`** → cursor is before the first row.
- **Expecting a sorted order** without `ORDER BY`.
- **Dropping try-with-resources** → leaked connections (next lessons show the disaster).

### Where it is used

This exact shape — open, prepare, bind, execute, iterate, close — is the bottom of every Java
database tool. The tools hide it, but it always runs underneath.

### Link to the task

This program is subtask 2, and the step table is subtask 3. Everything after — defeating injection,
surviving leaks, the `JdbcClient` rewrite — builds on this program.

### Do it yourself — write and run raw JDBC (blank laptop)

With Postgres running and seeded (Lesson 3's "Before you start") and the driver jar present:

1. Create `RawJdbc.java` with the code from the Example above.
2. Compile: `javac -cp .:postgresql-42.7.4.jar RawJdbc.java`.
3. Run for Hyderabad: `java -cp .:postgresql-42.7.4.jar RawJdbc` → four events.
4. Run for Mumbai: `java -cp .:postgresql-42.7.4.jar RawJdbc Mumbai` → three events.
5. Write down the seven manual steps from the table above (subtask 3).

**Done when:** the program prints the events for a city passed as an argument, and you can list the
seven manual steps from memory.

### Summary for revision

- The full program = connect + prepare + bind + execute + step + read + close, with a join.
- try-with-resources closes `rs`, then `ps`, then `c`, in the correct order.
- Seven manual steps per query; higher tools remove most, but never the SQL.
- Without `ORDER BY`, row order is not guaranteed.

### Check questions, with answers

**Question 1.** The outer header declares `Connection c` first, then `PreparedStatement ps`; the
`ResultSet rs` is declared in the inner block. When the program finishes, in what order do these
three close, and why is that order correct?

**Answer.** They close `rs` first, then `ps`, then `c`. The inner try-with-resources ends first, so
`rs` closes; then the outer block ends and closes its resources in reverse declaration order, `ps`
then `c`. This is correct because `rs` depends on `ps`, which depends on `c`: you must close the
inner, dependent resources before the outer ones they rely on.

**Question 2.** A colleague says: "The `JdbcClient` version will be shorter because it writes the
SQL for me." Based on the step table, is that right? Which step does `JdbcClient` not remove, and
what must you still do yourself?

**Answer.** It will be shorter, but the reason is wrong: `JdbcClient` does not write the SQL for
you. The table removes the seven mechanical steps (open, prepare, bind, execute, step, read, close),
not the SQL itself. You still author the SQL string (with named holes like `:city`), so you still
know exactly what query runs — that is the deliberate design, not an accident.

### Your doubts

None recorded for this lesson.

---

## Lesson 10 — SQL injection (when input becomes code)

### Definition

**SQL injection** is an attack where user input is pasted into a query and runs as SQL code instead
of being treated as data. It happens whenever you build a SQL string by gluing a value into it with
`+` (concatenation).

### Why it is needed (to understand)

This whole task exists to break the string-concatenation habit. SQL injection is among the oldest
and most damaging attacks and still appears in real breach reports every year. Running it once,
against your own code, is what makes you never write concatenated SQL again.

### Key points

- Concatenation puts the value *inside* the SQL text, so crafted input can become SQL.
- `' OR '1'='1` makes a `WHERE` always true — every row leaks.
- `'; DROP TABLE x; --` adds a whole new command and comments out the leftover quote.
- The database cannot tell developer-intended data from attacker-added SQL in one glued string.

### How it works, step by step

1. Vulnerable code: `"... WHERE v.city = '" + city + "'"` — value inside the SQL string.
2. The database receives one finished string and parses all of it as SQL.
3. `' OR '1'='1` → `city = '' OR '1'='1'` → always true → every row.
4. `'; DROP TABLE scratch; --` → a `SELECT`, then a `DROP TABLE`, then a comment hiding the leftover
   quote.
5. The input stopped being data and became code — that is injection.

### Example

```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class SqlInjectionDemo {
    static final String URL  = "jdbc:postgresql://localhost:5432/zenith";
    static final String USER = "postgres";
    static final String PASS = "dev";

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, USER, PASS)) {
            System.out.println("=== PART A: concatenated SELECT, normal vs malicious input ===");
            vulnerableSearch(c, "Hyderabad");          // honest input
            vulnerableSearch(c, "' OR '1'='1");        // attack input

            System.out.println("\n=== PART B: concatenated statement drops a throwaway table ===");
            try (Statement s = c.createStatement()) {
                s.execute("DROP TABLE IF EXISTS scratch");
                s.execute("CREATE TABLE scratch (note text)");
                s.execute("INSERT INTO scratch VALUES ('I am important data')");
            }
            System.out.println("Before attack, scratch table exists? " + tableExists(c, "scratch"));

            String evilCity = "'; DROP TABLE scratch; --";
            String sql = "SELECT e.id, e.name FROM event e JOIN venue v ON v.id = e.venue_id "
                       + "WHERE v.city = '" + evilCity + "'";
            System.out.println("SQL actually sent:\n  " + sql);
            try (Statement s = c.createStatement()) {
                s.execute(sql);          // runs the SELECT, THEN the DROP TABLE, THEN a comment
            }
            System.out.println("After attack, scratch table exists? " + tableExists(c, "scratch"));
        }
    }

    static void vulnerableSearch(Connection c, String city) throws Exception {
        String sql = "SELECT e.id, e.name FROM event e JOIN venue v ON v.id = e.venue_id "
                   + "WHERE v.city = '" + city + "'";
        System.out.println("\ncity input = \"" + city + "\"");
        System.out.println("SQL actually sent:\n  " + sql);
        int count = 0;
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) count++;
        }
        System.out.println("rows returned: " + count);
    }

    static boolean tableExists(Connection c, String name) throws Exception {
        try (ResultSet rs = c.getMetaData().getTables(null, null, name, null)) {
            return rs.next();
        }
    }
}
```

Real output:

```text
=== PART A: concatenated SELECT, normal vs malicious input ===

city input = "Hyderabad"
SQL actually sent:
  SELECT e.id, e.name FROM event e JOIN venue v ON v.id = e.venue_id WHERE v.city = 'Hyderabad'
rows returned: 4

city input = "' OR '1'='1"
SQL actually sent:
  SELECT e.id, e.name FROM event e JOIN venue v ON v.id = e.venue_id WHERE v.city = '' OR '1'='1'
rows returned: 7

=== PART B: concatenated statement drops a throwaway table ===
Before attack, scratch table exists? true
SQL actually sent:
  SELECT e.id, e.name FROM event e JOIN venue v ON v.id = e.venue_id WHERE v.city = ''; DROP TABLE scratch; --'
After attack, scratch table exists? false
```

### Walkthrough

1. **Honest input "Hyderabad":** SQL `... city = 'Hyderabad'` → 4 rows.
2. **Attack `' OR '1'='1`:** the code wrote `city = '`, glued the value, then added `'`, giving
   `city = '' OR '1'='1'`. Read as `city = ''` OR `'1'='1'`; the second is always true, so the whole
   condition is true for every row — all 7 events leak, filter bypassed.
3. **Attack `'; DROP TABLE scratch; --`:** becomes `... city = ''; DROP TABLE scratch; --'`. Three
   pieces: a harmless select of nothing, then `DROP TABLE scratch` as a new command, then `--`
   commenting out the leftover quote. The table is destroyed; `exists?` goes `true` → `false`.

### Important terms

| Term | Meaning |
| --- | --- |
| **SQL injection** | Untrusted input running as SQL because it was concatenated into the query. |
| **Concatenation** | Building SQL by gluing values in with `+`; the root cause. |
| **`' OR '1'='1`** | Classic payload making a `WHERE` always true. |
| **`--`** | A SQL comment to end of line; hides leftover characters after an injected command. |

### Common mistakes that cause it

- **Escaping quotes by hand.** Error-prone; attackers find edge cases. Use bind parameters.
- **"It's a number, so it's safe."** Concatenating any value is injectable; the problem is
  concatenation, not the type.
- **"Only login forms are at risk."** Any concatenated input is — search, sort, filters, anything.
- **"My framework protects me."** Only while you use its parameterised API; concatenation re-exposes
  you.

### Where it is seen

Search boxes, login forms, URL parameters, API fields, sort and filter options. Real damage: whole
tables stolen, authentication bypassed (`' OR '1'='1' --`), data changed or destroyed. A permanent
fixture of the industry's most-dangerous-vulnerability lists.

### Link to the task

This is subtask 4: the `' OR '1'='1` leak and the `'; DROP TABLE scratch; --` destruction, both on
your own code. The fix is the next lesson (subtask 5).

### Do it yourself — perform the injection (blank laptop)

With Postgres running and seeded (Lesson 3's "Before you start") and the driver jar present. Only
ever run this against your own throwaway `zenith` database.

1. Create `SqlInjectionDemo.java` with the code from the Example above.
2. Compile: `javac -cp .:postgresql-42.7.4.jar SqlInjectionDemo.java`.
3. Run: `java -cp .:postgresql-42.7.4.jar SqlInjectionDemo`.

Expected: Hyderabad → 4 rows; `' OR '1'='1` → 7 rows (all events); scratch exists `true` before and
`false` after.

**Done when:** you have leaked every row with `' OR '1'='1` and dropped the `scratch` table via
injection, against your own code.

### Summary for revision

- Injection = input running as SQL because it was concatenated into the query.
- `' OR '1'='1` makes a `WHERE` always true (leak); `'; DROP TABLE x; --` runs a new command.
- The database cannot tell data from code in one glued string.
- Never concatenate untrusted input into SQL — the fix is bind parameters (next lesson).

### Check questions, with answers

**Question 1.** A login feature runs `String sql = "SELECT * FROM users WHERE name = '" + name + "'";`
and an attacker submits `' OR '1'='1` for `name`. Write the exact SQL sent, and explain why it
returns every user.

**Answer.** The SQL sent is `SELECT * FROM users WHERE name = '' OR '1'='1'`. The attacker's leading
`'` closes the empty string the code opened (`name = ''`), then `OR '1'='1'` is appended (the code's
trailing `'` completes the `'1'` literal). Since `'1'='1'` is always true, the condition is
`false OR true` = true for every row, so all users are returned.

**Question 2.** The attack value in Part B ended with `--`, giving
`... WHERE v.city = ''; DROP TABLE scratch; --'`. What does `--` do, why is it at the end, and what
would break for the attacker without it?

**Answer.** `--` begins a SQL comment that runs to the end of the line, so everything after it is
ignored. The attacker put it there to comment out the leftover `'` that the original code appends
after the value. Without the `--`, that dangling single quote would make the statement invalid SQL,
and the database would raise a syntax error instead of running the `DROP` — so the `--` is what keeps
the injected command valid.

### Your doubts

None recorded for this lesson.

---

## Lesson 11 — The fix: a bind parameter is parsed before the value arrives

### Definition

The fix for SQL injection is the `?` placeholder with `setString` — a **bind parameter**. The same
value that was an attack when glued into the SQL becomes harmless data when bound. No manual
escaping: never concatenate the value, always bind it.

### Why it is needed

Concatenation lets input become code. A fix must make that impossible, not merely unlikely.
Hand-escaping quotes is a losing game. Bind parameters close the hole completely, because of how the
database handles the SQL and the value — in two separate phases.

### Key points

- **Parse-then-bind:** the SQL (with `?`) is parsed and planned first; the value arrives afterward.
- The value is sent separately, as data, and never passes through the parser.
- So quotes, `;`, `OR`, `DROP` inside a value are just characters, never SQL.
- One-liner: *the SQL was parsed before the value ever arrived, so the value can never become SQL.*
- Placeholders are for **values** only, not column/table names.

### How it works, step by step

1. **Parse phase.** You send the SQL text with the `?` still in it. The database parses it into a
   fixed structure ("a `SELECT`, with a `WHERE` comparing `v.city` to one supplied value") and plans
   it. No value exists yet; the shape is locked. **Parsing** = recognising which parts are commands,
   tables, and columns, and where the value-slot is.
2. **Bind phase.** You send the value separately, labelled as data. The database drops it into the
   value-slot of the locked plan and compares it against the column.
3. The value never re-enters the parser, so its characters can only be data — never keywords,
   operators, or new statements.

### Example

```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class SqlInjectionFixedDemo {
    static final String URL  = "jdbc:postgresql://localhost:5432/zenith";
    static final String USER = "postgres";
    static final String PASS = "dev";

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, USER, PASS)) {
            String sql = "SELECT e.id, e.name FROM event e JOIN venue v ON v.id = e.venue_id "
                       + "WHERE v.city = ?";

            System.out.println("=== Parameterised SELECT: same malicious inputs, now harmless ===");
            safeSearch(c, sql, "Hyderabad");                   // honest input -> 4
            safeSearch(c, sql, "' OR '1'='1");                 // attack input -> 0
            safeSearch(c, sql, "'; DROP TABLE scratch; --");   // attack input -> 0

            System.out.println("\n=== The DROP TABLE attack, bound as a parameter ===");
            try (Statement s = c.createStatement()) {
                s.execute("DROP TABLE IF EXISTS scratch");
                s.execute("CREATE TABLE scratch (note text)");
            }
            System.out.println("Before: scratch exists? " + tableExists(c, "scratch"));
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, "'; DROP TABLE scratch; --");
                try (ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) count++;
                    System.out.println("rows returned: " + count);
                }
            }
            System.out.println("After:  scratch exists? " + tableExists(c, "scratch"));
        }
    }

    static void safeSearch(Connection c, String sql, String city) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, city);
            try (ResultSet rs = ps.executeQuery()) {
                int count = 0;
                while (rs.next()) count++;
                System.out.println("city input = \"" + city + "\"  ->  rows returned: " + count);
            }
        }
    }

    static boolean tableExists(Connection c, String name) throws Exception {
        try (ResultSet rs = c.getMetaData().getTables(null, null, name, null)) {
            return rs.next();
        }
    }
}
```

Real output:

```text
=== Parameterised SELECT: same malicious inputs, now harmless ===
city input = "Hyderabad"  ->  rows returned: 4
city input = "' OR '1'='1"  ->  rows returned: 0
city input = "'; DROP TABLE scratch; --"  ->  rows returned: 0

=== The DROP TABLE attack, bound as a parameter ===
Before: scratch exists? true
rows returned: 0
After:  scratch exists? true
```

### Walkthrough

1. **"Hyderabad"** → 4 rows. Normal search.
2. **`' OR '1'='1`** → 0 rows. The whole string is compared as one value against `city`; no city is
   named that, so nothing matches. The `OR` is just letters. (Concatenated, the same input returned
   all 7 rows.)
3. **`'; DROP TABLE scratch; --`** → 0 rows. Compared as a city name; matches nothing.
4. **DROP attack, bound:** the table exists before and after (`true`/`true`) — the `DROP` never ran.
   The difference from the injection lesson is entirely concatenate-vs-bind.

### Important terms

| Term | Meaning |
| --- | --- |
| **Bind parameter** | A value supplied for a `?`; delivered as data, after parsing. |
| **Parse** | Turning SQL text into a fixed, structured query the database can run. |
| **Parse-then-bind** | Parse the SQL (with `?`) first; bind the value afterward, as data. |
| **Allow-list** | A fixed set of permitted values (used for column/table names, which can't be `?`). |

### Common mistakes (how people still get injected with prepared statements)

- **Concatenating an identifier.** `?` works only for values, not column/table names. `"... ORDER BY "
  + sortColumn` is injectable again. Fix: validate against an allow-list of real column names.
- **Preparing an already-glued string.** `prepareStatement("... city = '" + city + "'")` is not safe;
  the value was concatenated before parsing. The `?` must be where the value goes.
- **Dynamic SQL inside the database.** A DB function that concatenates input into an `EXECUTE` string
  re-creates the hole server-side.

### Where it is used

Everywhere, for every value, no exceptions. Raw JDBC uses positional `?`; Spring's `JdbcClient` uses
named `:city`. Both give parse-then-bind protection.

### Link to the task

This is subtask 5, done, and it answers **checkpoint question 1**. The task notes it is the first item
in the later application-security-hardening task.

### Do it yourself — defeat the attack with a bind parameter (blank laptop)

With Postgres running and seeded (Lesson 3's "Before you start") and the driver jar present:

1. Create `SqlInjectionFixedDemo.java` with the code from the Example above.
2. Compile: `javac -cp .:postgresql-42.7.4.jar SqlInjectionFixedDemo.java`.
3. Run: `java -cp .:postgresql-42.7.4.jar SqlInjectionFixedDemo`.

Expected: Hyderabad → 4; `' OR '1'='1` → 0; `'; DROP TABLE scratch; --` → 0; and the table exists
`true` both before and after the bound DROP attempt.

**Done when:** the same inputs that attacked the concatenated version now return 0 rows and leave the
table intact, and you can state why: the SQL was parsed before the value arrived.

### Summary for revision

- Bind parameters make injection impossible by construction: parse-then-bind.
- The value is data, delivered after parsing; it never becomes SQL.
- Placeholders are for values only; validate identifiers against an allow-list.
- Never concatenate untrusted input; always bind.

### Check questions, with answers

**Question 1 (checkpoint question 1).** Binding `' OR '1'='1` returned 0 rows, while concatenating it
returned all 7 and could drop a table. In terms of what the database does with the SQL text and the
value separately, why is the bound version immune to injection?

**Answer.** A `PreparedStatement` sends the SQL text (with the `?`) first; the database parses and
plans it then, locking the query's structure while the value-slot is still empty. The value is sent
separately, as a typed data value, and is slotted into the locked plan — compared against the column
but never passed through the parser. Because the value arrives after parsing and never re-enters the
parser, its characters (quotes, `;`, `OR`, `DROP`) can only be data, never SQL. So `' OR '1'='1` is
just a city name that matches nothing, and the DROP text is just a city name, so the table survives.
Concatenation instead puts the value into the text the parser reads, so crafted characters become SQL.

**Question 2.** A developer binds the search value but builds the sort column by concatenation:
`String sql = "SELECT id, name FROM event ORDER BY " + sortColumn;` then `c.prepareStatement(sql)`
with no `?`, where `sortColumn` comes from the user. (a) Is this safe? Why or why not? (b) What is a
safe way to let a user choose the sort column?

**Answer.** (a) Not safe. The sort column is concatenated into the SQL text before parsing, so it goes
through the parser; a crafted `sortColumn` can inject SQL. `prepareStatement` does not help because
there is no `?` for the identifier and the dangerous text is already in the statement. (b) Use an
allow-list: define the permitted column names (for example `name`, `starts_at`), check the user's
choice is one of them, and only then place the validated name into the SQL — rejecting anything else.
Placeholders cannot stand for identifiers, so validation against a fixed list is the safe path.

### Your doubts

None recorded for this lesson.

---

## Lesson 12 — Connection pools and HikariCP

### Definition

A **connection pool** is a box of already-open database connections that the app keeps and lends out.
Code **borrows** a connection, uses it, and **returns** it; the connection stays open the whole time.
**HikariCP** is the pool library Spring Boot uses automatically.

### Why it is needed

Opening a connection is expensive (network call, "who are you?", password check, session setup — the
first open measured about 195 ms; warm repeats still take milliseconds). A busy app paying that per
request wastes time and floods Postgres with hellos and goodbyes. The pool pays the opening cost a few
times at startup, then reuses those connections forever. The pool's **maximum pool size** (HikariCP
default: **10**) is also a hard ceiling that protects the database from one app grabbing unlimited
connections.

### Key points

- Borrow = `pool.getConnection()`; no network, no password check — microseconds.
- **With a pool, `close()` does not hang up — it returns the connection to the pool.** The pool hands
  out a wrapper (`HikariProxyConnection`) whose `close()` means "return me".
- Default maximum pool size: **10** (write this down for subtask 9).
- If all 10 are borrowed, the next borrower waits (default up to 30 s), then gets a timeout error.
- The max size limits how many connections the app can use at the same moment = how many queries it
  can have in flight at once = its maximum load on Postgres.

### How it works, step by step

1. At startup the pool opens a connection (more in the background as needed, never above the maximum)
   and logs `HikariPool-1 - Starting...` / `Start completed.`
2. `pool.getConnection()` hands over an already-open connection in microseconds.
3. The code uses it exactly like raw JDBC: `prepareStatement`, bind, `executeQuery`, `ResultSet`.
4. `close()` (via try-with-resources) returns it to the box, still open.
5. Only when the pool itself shuts down are the real hang-ups done.

### Example

```java
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.DriverManager;

public class PoolDemo {
    static final String URL  = "jdbc:postgresql://localhost:5432/zenith";
    static final String USER = "postgres";
    static final String PASS = "dev";

    public static void main(String[] args) throws Exception {
        System.out.println("=== Part A: a fresh connection every time (no pool) ===");
        for (int i = 1; i <= 5; i++) {
            long start = System.nanoTime();
            try (Connection c = DriverManager.getConnection(URL, USER, PASS)) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                System.out.println("open #" + i + ": took " + ms + " ms");
            }   // close() here really hangs up the connection
        }

        System.out.println();
        System.out.println("=== Part B: borrowing from a HikariCP pool ===");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername(USER);
        config.setPassword(PASS);
        // note: we do NOT set a pool size, so HikariCP uses its default

        try (HikariDataSource pool = new HikariDataSource(config)) {
            System.out.println("default maximum pool size: " + pool.getMaximumPoolSize());

            for (int i = 1; i <= 5; i++) {
                long start = System.nanoTime();
                try (Connection c = pool.getConnection()) {   // borrow
                    long us = (System.nanoTime() - start) / 1_000;
                    System.out.println("borrow #" + i + ": got " + c.getClass().getSimpleName()
                            + " in " + us + " microseconds");
                }   // close() here does NOT hang up; it returns the connection to the pool
            }
        }   // closing the pool itself hangs up all its connections
    }
}
```

Real output:

```text
=== Part A: a fresh connection every time (no pool) ===
open #1: took 191 ms
open #2: took 13 ms
open #3: took 7 ms
open #4: took 6 ms
open #5: took 6 ms

=== Part B: borrowing from a HikariCP pool ===
[main] INFO com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Starting...
[main] INFO com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@6166e06f
[main] INFO com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Start completed.
default maximum pool size: 10
borrow #1: got HikariProxyConnection in 1007 microseconds
borrow #2: got HikariProxyConnection in 6 microseconds
borrow #3: got HikariProxyConnection in 3 microseconds
borrow #4: got HikariProxyConnection in 5 microseconds
borrow #5: got HikariProxyConnection in 2 microseconds
[main] INFO com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Shutdown initiated...
[main] INFO com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Shutdown completed.
```

### Walkthrough

1. **Part A:** first fresh open 191 ms (driver loading plus the full network-and-password dance);
   warm opens still 6–13 **milli**seconds each, every time, forever.
2. **`HikariPool-1 - Starting... / Added connection org.postgresql.jdbc.PgConnection@... / Start
   completed.`** — the pool opened a real `PgConnection` at startup and kept it. Spring Boot prints
   the same lines at boot — this is the log line subtask 9 asks for.
3. **`default maximum pool size: 10`** — no size was set, so this is HikariCP's built-in default.
4. **Borrows: 2–6 microseconds** (first borrow 1007 µs — one-time bookkeeping). A microsecond is a
   thousandth of a millisecond, so a 6 ms open = 6,000 µs: the pool is roughly a thousand times
   faster, because borrowing does no network call and no password check.
5. **`HikariProxyConnection`** — the pool's wrapper around the real connection; its `close()` returns
   instead of hanging up, which is why try-with-resources is still correct and still required.
6. **`Shutdown initiated... / completed.`** — the pool closing is when real hang-ups happen.

### Important terms

| Term | Meaning |
| --- | --- |
| **Connection pool** | A kept box of open connections, lent out and returned. |
| **HikariCP** | The pool library Spring Boot uses by default. |
| **Borrow / return** | `getConnection()` takes from the box; `close()` puts back into the box. |
| **Maximum pool size** | Hard ceiling on connections the pool will hold. HikariCP default: 10. |
| **Connection timeout** | How long a borrower waits when the box is empty (default 30 s), then errors. |
| **Proxy** | The wrapper (`HikariProxyConnection`) that redefines `close()` as "return". |

### Common mistakes

- Thinking `close()` kills a pooled connection — it returns it. Try-with-resources means "always give
  it back".
- Forgetting `close()` with a pool: the connection is never returned; after 10 such mistakes the box
  is empty and every borrower times out. That is a connection leak.
- Setting a huge pool size "for speed": Postgres allows a limited total (commonly about 100, shared by
  all apps); ten busy connections usually beat a hundred idle ones.
- Borrowing early and holding long: borrowing is cheap, holding is not — only 10 exist. Borrow late,
  return early.

### Where it is used

Every serious Java app. Spring Boot starts a HikariCP pool automatically when the JDBC starter and a
database URL are present; `JdbcClient` borrows from and returns to it under the hood.

### Link to the task

Answers **checkpoint question 3** (what a pool is for; what its size limits) and supplies the subtask 9
fact: HikariCP's default maximum pool size is **10** (to be confirmed in the Spring startup log).

### Do it yourself — see the pool and its default size (blank laptop)

Postgres running and seeded (Lesson 3's "Before you start"), Postgres driver jar present.

1. Download three jars into the folder with your `.java` files:

   ```bash
   curl -O https://repo1.maven.org/maven2/com/zaxxer/HikariCP/5.1.0/HikariCP-5.1.0.jar
   curl -O https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13.jar
   curl -O https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.13/slf4j-simple-2.0.13.jar
   ```

   (HikariCP needs the slf4j logging library; `slf4j-simple` prints the log lines so you can see them.)

2. Create `PoolDemo.java` with the code from the Example above.
3. Compile and run (Mac/Linux; on Windows use `;` instead of `:` between jars):

   ```bash
   javac -cp .:HikariCP-5.1.0.jar PoolDemo.java
   java -cp .:postgresql-42.7.4.jar:HikariCP-5.1.0.jar:slf4j-api-2.0.13.jar:slf4j-simple-2.0.13.jar -Dorg.slf4j.simpleLogger.logFile=System.out PoolDemo
   ```

   The `-D...logFile=System.out` part makes the logging library print to normal output instead of the
   error stream, so lines appear in order.

Expected: milliseconds in Part A; `HikariPool-1 - Starting...` / `Start completed.`; `default maximum
pool size: 10`; microsecond borrows; two `Shutdown` lines.

Failures: `NoClassDefFoundError: org/slf4j/LoggerFactory` = a jar missing from `-cp`; `Connection to
localhost:5432 refused` = Postgres not running.

**Done when:** you can point at the `HikariPool-1` line, say the default pool size from memory, and
explain what `close()` does differently when a pool is involved.

### Summary for revision

- A pool keeps connections open and lends them out; borrowing is ~1000× faster than opening.
- HikariCP = Spring Boot's default pool; default maximum size = **10**.
- The size limits simultaneous connections = queries in flight = load on Postgres.
- With a pool, `close()` returns the connection; empty box makes borrowers wait, then time out.

### Check questions, with answers

**Question 1.** A teammate says: "Our Spring Boot app uses HikariCP with its default settings. That
means unlimited database connections, right? And calling `close()` on a connection destroys it, so we
avoid `close()` to keep connections alive." Both claims are wrong. What is the real limit, what does
that limit control, and what does `close()` actually do when a pool is in use?

**Answer.** The real limit is HikariCP's maximum pool size, default **10** — a hard ceiling, not
unlimited. It controls how many connections the app can hold at the same moment, which is the same as
how many queries it can have in flight at once and how much load it can put on Postgres; if all 10 are
borrowed, the next borrower waits (default up to 30 s) and then gets a timeout error. And `close()` on
a pooled connection destroys nothing: the pool hands out a wrapper whose `close()` returns the
connection to the pool, still open. Avoiding `close()` is exactly backwards — unreturned connections
empty the box until every borrower times out. Always close (try-with-resources); with a pool, closing
IS the return.

**Question 2.** With a pool, borrowing took about 2–6 microseconds, while opening a fresh connection
took 6–191 milliseconds. Both end with the code holding a working connection — so where does the saved
time come from? Name the work a fresh open must do that a borrow skips.

**Answer.** A fresh open must establish the connection: make the network link to the server, go
through the authentication dance ("who are you?", password check), and set up the session — plus
first-time driver loading on the very first open. A borrow skips all of it: the line is already open
and already logged in, so the pool just hands over an existing connection from the box, a tiny
in-memory operation. That is why borrowing is microseconds while opening is milliseconds.

### Your doubts

None recorded for this lesson.

---

## Lesson 13 — The connection leak: open 200, close none

### Definition

A **connection leak** is when code opens (or borrows) a connection and never closes (or returns) it.
Nothing crashes at that moment; the occupied connections pile up, one per forgotten close, until
something runs out.

### Why it is needed

It is the classic slow-motion production outage: one missing `close()` in a rarely-used code path,
fine in testing, dead three days later under load — with the error appearing far from the bug. The
task has you cause it on purpose so you can recognise it in a log.

### Key points

- Without a pool: each open connection occupies a slot on the **Postgres server**. The server's limit
  is the `max_connections` setting — **100** by default. Full server refuses **everyone**:
  `FATAL: sorry, too many clients already`.
- With a pool: after 10 unreturned borrows (HikariCP default max), the box is empty; borrower 11 waits
  (default 30 s), then gets `SQLTransientConnectionException: ... Connection is not available, request
  timed out ... (total=10, active=10, idle=0, waiting=0)`. Only this app suffers; Postgres holds just
  10 connections, so the pool caps the damage.
- `active=total, idle=0` in a log = say it out loud: connection leak (or a pool genuinely too small).
- Same bug, same fix in both worlds: try-with-resources, which cannot be skipped — Java calls
  `close()` even when an exception flies out of the block.

### How it works, step by step

1. Each loop iteration opens a connection and keeps a reference; nobody closes.
2. **No pool:** server slots fill; at slot 101 of 100 Postgres itself refuses (`FATAL` = the message
   came from the server — we reached it and it turned us away).
3. **Pool:** borrows 1–10 instantly empty the box; borrow 11 waits the connection timeout, then fails
   with the pool's statistics in the message.
4. Restarting the app releases everything — the symptom vanishes and builds up again. A restart is a
   clue, not a fix. Raising limits just moves the wall.

### Example

Ask Postgres for its limit first:

```bash
docker exec pg psql -U postgres -c "SHOW max_connections;"
```

```text
 max_connections 
-----------------
 100
```

The leak in both worlds (the `held` list keeps every connection reachable so none is closed until the
demo's cleanup):

```java
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

public class LeakDemo {
    static final String URL  = "jdbc:postgresql://localhost:5432/zenith";
    static final String USER = "postgres";
    static final String PASS = "dev";

    public static void main(String[] args) throws Exception {
        partA_leakWithoutPool();
        System.out.println();
        partB_leakWithPool();
    }

    // Part A: no pool. Open connections in a loop of 200 and never close them.
    static void partA_leakWithoutPool() {
        System.out.println("=== Part A: no pool - open 200 connections, close none ===");
        List<Connection> held = new ArrayList<>();   // holds every connection so none is ever closed
        try {
            for (int i = 1; i <= 200; i++) {
                held.add(DriverManager.getConnection(URL, USER, PASS));   // no try-with-resources!
                if (i % 20 == 0) System.out.println("opened " + i + " connections, closed none...");
            }
            System.out.println("opened all 200 (unexpected!)");
        } catch (Exception e) {
            System.out.println();
            System.out.println("opened " + held.size() + " connections, then the server refused:");
            System.out.println("  error type:    " + e.getClass().getSimpleName());
            System.out.println("  error message: " + e.getMessage().trim());
        } finally {
            for (Connection c : held) {
                try { c.close(); } catch (Exception ignore) {}
            }
            System.out.println("(cleanup: closed all " + held.size() + " so the database is usable again)");
        }
    }

    // Part B: the same bug behind a HikariCP pool - borrow and never return.
    static void partB_leakWithPool() {
        System.out.println("=== Part B: HikariCP pool - borrow 200 connections, return none ===");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername(USER);
        config.setPassword(PASS);
        config.setConnectionTimeout(2000);   // wait at most 2 s for a free connection (default is 30 s)

        List<Connection> held = new ArrayList<>();
        try (HikariDataSource pool = new HikariDataSource(config)) {
            try {
                for (int i = 1; i <= 200; i++) {
                    held.add(pool.getConnection());   // borrow, never return
                    System.out.println("borrowed " + i + ", returned none...");
                }
                System.out.println("borrowed all 200 (unexpected!)");
            } catch (Exception e) {
                System.out.println();
                System.out.println("borrow number " + (held.size() + 1) + " failed:");
                System.out.println("  error type:    " + e.getClass().getSimpleName());
                System.out.println("  error message: " + e.getMessage());
            } finally {
                for (Connection c : held) {
                    try { c.close(); } catch (Exception ignore) {}
                }
            }
        }
    }
}
```

Real output:

```text
=== Part A: no pool - open 200 connections, close none ===
opened 20 connections, closed none...
opened 40 connections, closed none...
opened 60 connections, closed none...
opened 80 connections, closed none...
opened 100 connections, closed none...

opened 100 connections, then the server refused:
  error type:    PSQLException
  error message: FATAL: sorry, too many clients already
(cleanup: closed all 100 so the database is usable again)

=== Part B: HikariCP pool - borrow 200 connections, return none ===
[main] INFO com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Starting...
[main] INFO com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@3d3fcdb0
[main] INFO com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Start completed.
borrowed 1, returned none...
borrowed 2, returned none...
borrowed 3, returned none...
borrowed 4, returned none...
borrowed 5, returned none...
borrowed 6, returned none...
borrowed 7, returned none...
borrowed 8, returned none...
borrowed 9, returned none...
borrowed 10, returned none...

borrow number 11 failed:
  error type:    SQLTransientConnectionException
  error message: HikariPool-1 - Connection is not available, request timed out after 2006ms (total=10, active=10, idle=0, waiting=0)
[main] INFO com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Shutdown initiated...
[main] INFO com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Shutdown completed.
```

### Walkthrough

1. **Part A:** asked for 200, got exactly **100** — precisely `max_connections`. Open 101 →
   `FATAL: sorry, too many clients already`. `FATAL` = the Postgres server itself refused. At that
   moment the database is unreachable for everybody (other apps, even `psql`).
2. **Part B:** borrows 1–10 = the whole pool. Borrow 11 waited the configured 2 s
   (`timed out after 2006ms`) and failed. The gold in the message: `total=10, active=10, idle=0` —
   10 connections exist, all lent out, none free. `SQLTransientConnectionException`: "transient" is
   the pool's optimism that a connection might come back. With a leak, it never does.
3. Postgres never saw a problem in Part B — 10 of 100 slots used. The pool turned a database-wide
   outage into a one-app outage.

### Important terms

| Term | Meaning |
| --- | --- |
| **Connection leak** | Opening/borrowing a connection and never closing/returning it. |
| **`max_connections`** | Postgres server setting: total connection slots (default 100). |
| **`FATAL: sorry, too many clients already`** | Server full; everyone refused. |
| **`Connection is not available, request timed out`** | Pool empty; this app's borrowers time out. |
| **`total / active / idle`** | Pool stats in the error: exist / lent out / free. |

### Common mistakes

- **Closing manually at the end of the method.** The last-line `c.close()` is skipped whenever an
  exception or early `return` happens before it — a leak that fires only on errors, the worst kind.
- **Believing the pool makes closing unnecessary.** Opposite: with a pool, `close()` IS the return.
- **"Fixing" by restart.** Releases everything, symptom vanishes, builds up again. Clue, not fix.
- **"Fixing" by raising `max_connections` or pool size.** Moves the wall; the leak still hits it.

### Where it is seen

Production logs: "too many clients" for hand-opened connections; "request timed out ... active=10,
idle=0" for Spring apps with a leak or an undersized pool. Leaks hide in code that closes on the happy
path but not the error path — reviewers reflexively look for `getConnection()` outside
try-with-resources.

### Link to the task

Subtask 6 done — try-with-resources removed, a loop of two hundred, the real error read. Answers
**checkpoint question 2**, and closes the loop on why try-with-resources is non-negotiable.

### Do it yourself — cause both leak errors (blank laptop)

Postgres running and seeded; all four jars (`postgresql-42.7.4.jar`, `HikariCP-5.1.0.jar`,
`slf4j-api-2.0.13.jar`, `slf4j-simple-2.0.13.jar`) next to your `.java` files.

1. Ask Postgres for its limit: `docker exec pg psql -U postgres -c "SHOW max_connections;"` → `100`.
2. Create `LeakDemo.java` with the code from the Example above.
3. Compile and run (Mac/Linux; Windows uses `;` between jars):

   ```bash
   javac -cp .:HikariCP-5.1.0.jar LeakDemo.java
   java -cp .:postgresql-42.7.4.jar:HikariCP-5.1.0.jar:slf4j-api-2.0.13.jar:slf4j-simple-2.0.13.jar -Dorg.slf4j.simpleLogger.logFile=System.out LeakDemo
   ```

Expected: Part A opens exactly 100 then `FATAL: sorry, too many clients already`; Part B borrows
exactly 10 then `Connection is not available, request timed out after ~2000ms (total=10, active=10,
idle=0, waiting=0)`. The run takes a few seconds (Part B deliberately waits 2 s).

**Safety:** while Part A holds its 100 connections nothing else can talk to this Postgres; the demo
closes everything in `finally`. Only run against your own throwaway `zenith` container. If you kill it
mid-run and the database stays full: `docker restart pg`.

**Done when:** you have both error messages in your terminal and can say which one means "the server
itself is full" and which one means "my app's pool is empty".

### Summary for revision

- A leak = open/borrow without close/return; damage piles up silently.
- No pool: fills the server's 100 `max_connections` slots → `FATAL: sorry, too many clients already`
  → everyone locked out.
- Pool: empties the 10-slot box → borrowers wait, then `request timed out (total=10, active=10,
  idle=0)` → only this app suffers; the pool caps the damage.
- Fix: try-with-resources, always — it closes even when exceptions fly.

### Check questions, with answers

**Question 1.** A Java program opens Postgres connections in a loop and never calls `close()`. The
server's `max_connections` is 100. Describe what happens as the loop runs: when does it fail, with
what error, and who else is affected? Then the same bug runs behind a HikariCP pool with its default
maximum of 10 — what changes about when it fails, the error, and who is affected?

**Answer.** Without a pool, each iteration occupies one server slot, so the loop succeeds exactly 100
times; open 101 fails with `PSQLException: FATAL: sorry, too many clients already`. `FATAL` means the
server itself refused. Everyone using this Postgres is affected — other apps and even `psql` are
locked out until connections are released. Behind a pool of 10, the first 10 borrows succeed and empty
the pool; borrow 11 waits the connection timeout (default 30 s) and then fails with
`SQLTransientConnectionException: ... Connection is not available, request timed out ... (total=10,
active=10, idle=0, waiting=0)`. Only this app is affected: Postgres holds just 10 of its 100 slots, so
other apps keep working — the pool caps the damage at its maximum size.

**Question 2.** This method closes its connection on the last line, yet it still leaks. Explain exactly
when the leak happens, and rewrite the method so the leak is impossible.

```java
static int countEvents(String url, String user, String pass) throws Exception {
    Connection c = DriverManager.getConnection(url, user, pass);
    PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM event");
    ResultSet rs = ps.executeQuery();
    rs.next();
    int n = rs.getInt(1);
    rs.close();
    ps.close();
    c.close();
    return n;
}
```

**Answer.** The leak fires whenever an exception is thrown after the connection opens but before the
close lines run — for example `executeQuery` failing (bad SQL, dropped table, network hiccup) or
`rs.next()`/`getInt` failing. The exception jumps out of the method immediately, skipping `rs.close()`,
`ps.close()`, and `c.close()`, so the connection is never closed. On the happy path it closes; on the
error path it leaks — and errors cluster under load. The fix is try-with-resources, which Java closes
in reverse order even when an exception flies:

```java
static int countEvents(String url, String user, String pass) throws Exception {
    try (Connection c = DriverManager.getConnection(url, user, pass);
         PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM event");
         ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getInt(1);
    }
}
```

### Your doubts

None recorded for this lesson.

---

## Lesson 14 — Wiring Spring Boot to Postgres: the starter, the driver, and `application.yml`

### Definition

Spring Boot replaces our manual wiring (driver jar on the classpath, `DriverManager`, hand-built pool)
with two declarations in **`pom.xml`** and three lines in **`application.yml`**.

- **Maven**: the build tool. It reads `pom.xml`, downloads the libraries named there (plus the
  libraries they need), compiles, and runs.
- **Dependency**: a `pom.xml` entry naming a library, fetched from the central warehouse.
- **Starter**: a Spring Boot dependency that is a bundle. `spring-boot-starter-jdbc` = Spring's JDBC
  helpers (including `JdbcClient`) **plus the HikariCP pool**, in versions tested together.
- **`application.yml`**: Spring Boot's settings file — where is the database (URL), who am I
  (username), prove it (password).

### Why it is needed

Manual wiring must be repeated per project: fetch the driver, type classpaths on every command, write
pool code, keep versions compatible. With the starter + `application.yml`, you declare *what* you need
and *where* the database is; Spring Boot assembles the same HikariCP machinery at startup.

### Key points

- The starter brings the pool and helpers but **not** the Postgres driver — add
  `org.postgresql:postgresql` yourself (Spring cannot guess your database).
- Dependencies carry no version numbers; the `spring-boot-starter-parent` manages matching versions.
- Spring Boot builds one **`DataSource`** (Java's standard "thing that gives out connections");
  concretely a `HikariDataSource` — maximum pool size 10 by default.
- Code asks for a `DataSource` as a parameter and Spring fills it in: that is **injection**.
- The pool opens its first real connection at first borrow — that is when
  `HikariPool-1 - Starting...` appears in the log (the subtask 9 line).

### How it works, step by step

1. Maven reads `pom.xml`, downloads the two dependencies and their needs into a local cache (first
   build slow, later builds fast).
2. At startup Spring Boot inspects the classpath: HikariCP present, Postgres driver present, and
   `spring.datasource.url/username/password` set in `application.yml` → "this app wants a Postgres
   connection pool".
3. It builds the `HikariDataSource` loaded with those three values, defaults untouched.
4. Any code asking for a `DataSource` gets this pool; `getConnection()` borrows, `close()` returns.

### Example — the whole project (built and run for real)

```text
zenith-app/
├── pom.xml
└── src/main/
    ├── java/zenith/ZenithApp.java
    └── resources/application.yml
```

**`pom.xml`** (subtask 1):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- The parent gives us Spring Boot's tested, matching versions for everything below. -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.16</version>
        <relativePath/>
    </parent>

    <groupId>zenith</groupId>
    <artifactId>zenith-app</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <!-- Subtask 1a: the JDBC starter - brings spring-jdbc, JdbcClient, and the HikariCP pool. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>

        <!-- Subtask 1b: the Postgres driver - the translator that speaks Postgres's wire language.
             scope "runtime" = our code never names driver classes; it is only needed when running. -->
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

**`src/main/resources/application.yml`** (subtask 2) — two spaces per level, never tabs:

```yaml
# Subtask 2: tell Spring where the database is and how to log in.
# Spring reads these three values at startup and builds the HikariCP pool from them.
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/zenith
    username: postgres
    password: dev
```

**`src/main/java/zenith/ZenithApp.java`** — proof the wiring worked. (`CommandLineRunner` = a block
Spring runs once after startup.)

```java
package zenith;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.Connection;

@SpringBootApplication
public class ZenithApp {

    public static void main(String[] args) {
        SpringApplication.run(ZenithApp.class, args);
    }

    // Spring sees the starter + application.yml and hands us a ready-made DataSource (the pool).
    // We never wrote a line of pool code or called DriverManager ourselves.
    @Bean
    CommandLineRunner proveWiring(DataSource dataSource) {
        return args -> {
            System.out.println(">>> Spring injected a DataSource of type: "
                    + dataSource.getClass().getSimpleName());
            System.out.println(">>> its maximum pool size: "
                    + ((HikariDataSource) dataSource).getMaximumPoolSize());
            try (Connection c = dataSource.getConnection()) {   // borrow from the pool
                System.out.println(">>> borrowed a connection to: "
                        + c.getMetaData().getDatabaseProductName() + " "
                        + c.getMetaData().getDatabaseProductVersion());
            }   // returned to the pool
        };
    }
}
```

Run with `mvn spring-boot:run`. Real output (trimmed to the interesting lines):

```text
 :: Spring Boot ::               (v3.5.16)

... Starting ZenithApp using Java 21.0.11 ...
... Started ZenithApp in 0.57 seconds ...
>>> Spring injected a DataSource of type: HikariDataSource
>>> its maximum pool size: 10
... com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Starting...
... com.zaxxer.hikari.pool.HikariPool        : HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@2e060819
... com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Start completed.
>>> borrowed a connection to: PostgreSQL 17.11
... HikariPool-1 - Shutdown initiated...
... HikariPool-1 - Shutdown completed.
```

Two wiring failures, also run for real:

- **Wrong password in `application.yml`** → startup dies with
  `Application run failed ... Caused by: org.postgresql.util.PSQLException: FATAL: password
  authentication failed for user "postgres"`. `FATAL` = the server was reached and said no; fix the
  `password:` line.
- **Driver dependency missing from `pom.xml`** → startup dies with
  `Failed to load driver class org.postgresql.Driver`. The pool is there, the translator is not; add
  the dependency.

### Walkthrough

1. `HikariDataSource` appears although our files never mention HikariCP — the starter brought it and
   Spring Boot chose it. (Spring Boot 3.5.16 ships HikariCP 6.3.3; same behaviour as the 5.1.0 we used
   by hand.)
2. `its maximum pool size: 10` — nobody set a size; that is the default. Subtask 9 confirmed inside a
   real Spring app.
3. `HikariPool-1 - Starting... / Start completed.` at first borrow, with a real
   `org.postgresql.jdbc.PgConnection` inside — made by the driver `pom.xml` pulled in.
4. `PostgreSQL 17.11` — same database as every raw-JDBC program; only the bookkeeping moved out of
   our code.

### Important terms

| Term | Meaning |
| --- | --- |
| **Maven** | Build tool; reads `pom.xml`, downloads dependencies, compiles, runs. |
| **`pom.xml`** | The project description file (dependencies live here). |
| **Starter** | A Spring Boot bundle dependency; `spring-boot-starter-jdbc` = JDBC helpers + HikariCP. |
| **`application.yml`** | Settings file; `spring.datasource.url/username/password`. |
| **`DataSource`** | Standard interface "gives out connections"; here concretely `HikariDataSource`. |
| **Injection** | Asking for an object as a parameter and Spring filling it in. |
| **`CommandLineRunner`** | A block Spring runs once after startup. |

### Common mistakes

- **Forgetting the driver dependency** → `Failed to load driver class org.postgresql.Driver`.
- **YAML indentation errors** (tabs, or `url:` not under `datasource:`) → the setting silently does
  not exist; startup complains no database is configured.
- **Pinning versions by hand** on each dependency → mismatched libraries; let the parent manage.
- **Treating startup errors as mysterious** — they are the same old triage: `FATAL: password
  authentication failed` = reached server, wrong credentials; `Connection ... refused` = nothing
  listening; `Failed to load driver class` = missing dependency.

### Where it is used

Every Spring service at work has this shape: database libraries in `pom.xml`, connection settings in
`application.yml` per environment (production real secrets come from environment variables, not typed
in the file — a later task covers that).

### Link to the task

Subtask 1 (starter + driver in `pom.xml`) done. Subtask 2 (`application.yml`) done. Subtask 9
(HikariCP line in the startup log; default pool size) done: `HikariPool-1 - Starting...`, maximum size
**10**.

### Do it yourself — boot Spring on Postgres (blank laptop)

Needs: Java 21 (`java -version`), Postgres container running and seeded (Lesson 3's "Before you
start").

1. **Install Maven** (just a folder, no admin rights):

   ```bash
   curl -O https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.tar.gz
   tar xzf apache-maven-3.9.9-bin.tar.gz
   export PATH="$PWD/apache-maven-3.9.9/bin:$PATH"
   mvn -version
   ```

   Expected: `Apache Maven 3.9.9` with your Java 21. (`export PATH` lasts only for this terminal
   window; rerun it in new windows.)

2. **Create the folders:**

   ```bash
   mkdir -p zenith-app/src/main/java/zenith zenith-app/src/main/resources
   ```

3. **Create the three files** exactly as in the Example: `zenith-app/pom.xml`,
   `zenith-app/src/main/resources/application.yml`,
   `zenith-app/src/main/java/zenith/ZenithApp.java`.

4. **Run:**

   ```bash
   cd zenith-app
   mvn spring-boot:run
   ```

   First run downloads many libraries (minutes); later runs start in about a second.

Expected: Spring banner, then `>>> Spring injected a DataSource of type: HikariDataSource`,
`>>> its maximum pool size: 10`, the `HikariPool-1 - Starting... / Start completed.` lines,
`>>> borrowed a connection to: PostgreSQL 17.x`, then the app exits by itself.

Failures: `FATAL: password authentication failed` → fix `password:` in `application.yml`;
`Connection to localhost:5432 refused` → start the container; `Failed to load driver class
org.postgresql.Driver` → driver dependency missing; "no datasource configured" complaints → check
YAML indentation.

**Done when:** the app boots clean, you can point at the `HikariPool-1` line, and for each `>>>` line
you can name which file made it possible.

### Summary for revision

- Two `pom.xml` dependencies: `spring-boot-starter-jdbc` (helpers + HikariCP) and
  `org.postgresql:postgresql` (the driver — never included automatically).
- Three `application.yml` lines: `spring.datasource.url / username / password`.
- Spring Boot builds a `HikariDataSource` from them; default max size 10; injected on request.
- `HikariPool-1 - Starting...` appears at first borrow; startup errors triage like all connection
  errors.

### Check questions, with answers

**Question 1.** A Spring Boot app has `spring-boot-starter-jdbc` and `org.postgresql:postgresql` in
`pom.xml`, and `application.yml` says `url: jdbc:postgresql://localhost:5432/zenith`,
`username: postgres`, `password: dev`. At startup a `DataSource` is injected into a runner, which
borrows one connection. In order: who downloads the libraries, who decides a HikariCP pool should
exist, where do the pool's URL and password come from, what concrete type is the injected
`DataSource`, and at what moment does `HikariPool-1 - Starting...` appear in the log?

**Answer.** **Maven** downloads the libraries: it reads `pom.xml`, fetches the starter (which brings
spring-jdbc and HikariCP) and the Postgres driver, plus everything they need. **Spring Boot** decides
the pool should exist: at startup it sees HikariCP and the driver on the classpath and
`spring.datasource.*` filled in, so it auto-builds the pool. The URL, username, and password come from
**`application.yml`** (the `spring.datasource` block). The injected `DataSource` is concretely a
**`HikariDataSource`**. And `HikariPool-1 - Starting...` appears at the **first borrow** — when the
runner calls `dataSource.getConnection()`, the pool opens its first real connection.

**Question 2.** A teammate's Spring Boot app dies at startup with `Caused by:
org.postgresql.util.PSQLException: FATAL: password authentication failed for user "postgres"`. They
start re-checking `pom.xml` dependencies and reinstalling the Postgres container. Based on what
`FATAL` tells you about where this message was born, what single file and line should they look at
instead — and what two other startup errors would have pointed at the container or at `pom.xml`?

**Answer.** `FATAL` messages are born **on the Postgres server**: the network path worked, the
container is up and listening, the driver loaded and spoke to the server — and the server rejected the
credentials. So the fix is in `application.yml`: the `password:` line (and `username:` if that was
changed) under `spring.datasource`. Reinstalling the container and auditing `pom.xml` is wasted work.
The errors that would point elsewhere: `Connection to localhost:5432 refused` means nothing is
listening — container or port problem; `Failed to load driver class org.postgresql.Driver` means the
driver dependency is missing from `pom.xml`.

### Your doubts

None recorded for this lesson.

---

## Lesson 15 — `JdbcClient`: named parameters and the row mapper

### Definition

**`JdbcClient`** is Spring's small helper for running SQL: one chain carrying the SQL text, the
parameter values, and a **row mapper** — a recipe that turns one result row into one Java object.

```java
db.sql("...").param("city", city).query(rowMapper).list()
```

It ships inside `spring-boot-starter-jdbc` (nothing new to install) and Spring injects a ready-made
one, the same way it injects the `DataSource`.

### Why it is needed

Of raw JDBC's seven numbered steps, only two were ever *our* problem: the SQL, and what to do with
each row. The other five (open a connection, prepare the statement, bind by position, walk the cursor,
close everything in order) were identical plumbing around every query, with a leak behind every
forgotten close. `JdbcClient` keeps the two real steps and does the plumbing — borrowing from the
HikariCP pool and returning the connection every time, exception or not.

### Key points

- **`.sql(text)`** — SQL with **named parameters**: `:city` instead of `?`. Still a bind parameter
  (parsed first, value delivered separately as data, injection impossible) — just found by name
  instead of by counting positions.
- **`.param("city", value)`** — binds value to name; several parameters = several `.param(...)` calls,
  any order.
- **`.query((rs, rowNum) -> ...)`** — the row mapper. `rs` is the same `ResultSet` we know, already
  positioned on a row. The mapper only reads that row's columns and builds one object — no loop, no
  `rs.next()`; `JdbcClient` drives the cursor and calls the mapper once per row.
- **`.list()`** — run everything: borrow, prepare, bind, execute, loop + map, close, return, hand back
  a `List`.
- Column types must match the mapper: `uuid` → `rs.getObject("id", UUID.class)`,
  timestamp-with-timezone → `rs.getObject("starts_at", OffsetDateTime.class)`; `getString`/`getInt`
  stay for text and numbers.

### Example

Same project as the Spring-wiring lesson — `pom.xml` and `application.yml` unchanged; only
`ZenithApp.java` changes. (A **record** is a tiny class that just carries values; `"""..."""` is a
**text block**, a string allowed to span lines — perfect for SQL.)

```java
package zenith;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@SpringBootApplication
public class ZenithApp {

    // One row of the result, as a Java object. A record: just carries the four values.
    record EventRow(UUID id, String name, String venue, OffsetDateTime startsAt) {}

    public static void main(String[] args) {
        SpringApplication.run(ZenithApp.class, args);
    }

    // Spring injects a ready-made JdbcClient. It borrows from the HikariCP pool
    // and returns the connection by itself - no Connection, no try-with-resources here.
    @Bean
    CommandLineRunner searchByCity(JdbcClient db) {
        return args -> {
            String city = args.length > 0 ? args[0] : "Hyderabad";

            List<EventRow> events = db.sql("""
                            SELECT e.id, e.name, v.name AS venue, e.starts_at
                            FROM event e
                            JOIN venue v ON v.id = e.venue_id
                            WHERE v.city = :city
                            ORDER BY e.starts_at
                            """)
                    .param("city", city)                       // bind the named parameter
                    .query((rs, rowNum) -> new EventRow(       // the row mapper: one row -> one object
                            rs.getObject("id", UUID.class),
                            rs.getString("name"),
                            rs.getString("venue"),
                            rs.getObject("starts_at", OffsetDateTime.class)))
                    .list();                                   // run, walk all rows, collect

            System.out.println(">>> events in " + city + ": " + events.size());
            for (EventRow e : events) {
                System.out.println(">>> " + e);
            }
        };
    }
}
```

Run `mvn spring-boot:run` (defaults to Hyderabad), then
`mvn spring-boot:run -Dspring-boot.run.arguments=Mumbai`. Real output:

```text
>>> events in Hyderabad: 4
>>> EventRow[id=aaaaaaaa-0000-0000-0000-000000000001, name=Monsoon Rock Night, venue=Riverside Arena, startsAt=2026-11-20T13:30Z]
>>> EventRow[id=aaaaaaaa-0000-0000-0000-000000000002, name=Classical Evening, venue=Riverside Arena, startsAt=2026-12-05T13:00Z]
>>> EventRow[id=aaaaaaaa-0000-0000-0000-000000000005, name=Sufi Night, venue=Riverside Arena, startsAt=2027-07-10T14:00Z]
>>> EventRow[id=aaaaaaaa-0000-0000-0000-000000000006, name=Indie Fest, venue=Riverside Arena, startsAt=2027-07-24T11:30Z]
```

```text
>>> events in Mumbai: 3
>>> EventRow[id=aaaaaaaa-0000-0000-0000-000000000003, name=Jazz at the Note, venue=The Blue Note, startsAt=2026-11-28T15:30Z]
>>> EventRow[id=aaaaaaaa-0000-0000-0000-000000000004, name=Open Mic Night, venue=The Blue Note, startsAt=2026-12-12T14:30Z]
>>> EventRow[id=aaaaaaaa-0000-0000-0000-000000000007, name=Blues Brunch, venue=The Blue Note, startsAt=2027-07-18T06:30Z]
```

### Walkthrough

1. Same four Hyderabad and three Mumbai events as the raw JDBC program — same database, same SQL
   shape, new plumbing. The mapper ran once per row; `.list()` collected the records.
2. **Type matching, learned the honest way:** the first version of this mapper read the id with
   `rs.getInt("id")` and died with a real error:

   ```text
   Caused by: org.postgresql.util.PSQLException: Bad value for type int : aaaaaaaa-0000-0000-0000-000000000001
   ```

   Decode: "you asked for an `int`, but the value is a UUID." Fix the mapper, not the SQL:
   `rs.getObject("id", UUID.class)`.
3. **Timezone note:** the seed says Monsoon Rock Night starts `19:00+05:30`; the output shows
   `13:30Z`. Same instant — `Z` is UTC, and 19:00 India time is 13:30 UTC. Postgres stores the
   instant; the driver reports it in UTC notation.
4. **Misspelled parameter, caused on purpose:** changing `.param("city", city)` to
   `.param("town", city)` fails before touching the database:

   ```text
   org.springframework.dao.InvalidDataAccessApiUsageException: No value supplied for the SQL parameter 'city': No value registered for key 'city'
   ```

   Names are checked and named out loud; positional `?` in the wrong order can silently run a wrong
   query instead.

### Important terms

| Term | Meaning |
| --- | --- |
| **`JdbcClient`** | Spring's fluent SQL helper: `sql → param → query(mapper) → list`. |
| **Named parameter** | `:city` — a bind parameter located by name, not position. |
| **Row mapper** | `(rs, rowNum) -> object`: reads the current row, builds one object. |
| **`.list()` / `.optional()` / `.single()`** | Collect all rows / expect 0-or-1 / expect exactly 1. |
| **`getObject(col, Type.class)`** | Read a column as a specific Java type (`UUID`, `OffsetDateTime`). |

### Common mistakes

- **Mapper type not matching the column type** → `Bad value for type int`, as above.
- **Misspelled or missing `.param`** → `No value supplied for the SQL parameter 'city'`. Every
  `:name` needs exactly one `.param("name", ...)`.
- **Concatenating into `.sql(...)`** — compiles, runs, reopens the injection hole. `JdbcClient` is
  only as safe as your use of parameters; values go through `:name`, always.
- **Driving the cursor inside the mapper** — `rs.next()` there fights the loop `JdbcClient` runs.
  Mappers only read the current row.
- **Reading a column the SELECT did not produce** — the mapper sees only the SELECT's column names;
  we read `venue` because the SQL says `v.name AS venue`.

### Where it is used

The bread-and-butter read path in modern Spring services (Boot 3.2+): repository classes full of small
methods, each `sql → param → query(mapper) → list()` (or `.optional()`/`.single()` for one-row
lookups), producing records the rest of the app passes around.

### Link to the task

**Subtask 7 done** — the city search rewritten with `JdbcClient`, a named parameter, and a row mapper.
Sets up subtask 8 and checkpoint question 4: what exactly did `JdbcClient` remove, and what did it
keep?

### Do it yourself — the search through JdbcClient (blank laptop)

Needs: the `zenith-app` project from the Spring-wiring lesson working (Maven on `PATH`, Postgres
running and seeded).

1. Replace the entire contents of `zenith-app/src/main/java/zenith/ZenithApp.java` with the Example
   code. `pom.xml` and `application.yml` stay as they are.
2. Run both searches:

   ```bash
   cd zenith-app
   mvn spring-boot:run
   mvn spring-boot:run -Dspring-boot.run.arguments=Mumbai
   ```

Expected: `>>> events in Hyderabad: 4` with four `EventRow[...]` lines; `>>> events in Mumbai: 3` with
three. Timestamps print in UTC (`13:30Z` = 19:00 India time).

Cause the two failures on purpose, then undo both: `.param("town", city)` → `No value supplied for the
SQL parameter 'city'`; `rs.getInt("id")` → `Bad value for type int`.

**Done when:** both cities return the right rows and you can explain each chain link (`sql`, `param`,
`query`, `list`) in one sentence.

### Summary for revision

- `JdbcClient` chain: `.sql(":city inside")` → `.param("city", v)` → `.query(mapper)` → `.list()`.
- Named parameters = bind parameters by name; same parse-then-bind injection safety as `?`.
- Row mapper reads the current row only; `JdbcClient` drives the cursor, closes, and returns the
  connection to the pool — always.
- Match Java types to column types with `getObject(col, Type.class)` for `uuid` and timestamps.

### Check questions, with answers

**Question 1.** In this chain:

```java
List<EventRow> events = db.sql("SELECT e.id, e.name FROM event e JOIN venue v ON v.id = e.venue_id WHERE v.city = :city")
        .param("city", "Mumbai")
        .query((rs, rowNum) -> new EventRow(rs.getObject("id", UUID.class), rs.getString("name")))
        .list();
```

what does `:city` do, and is it as injection-proof as the `?` placeholder was? And name the plumbing
steps from the raw JDBC days (connection, statement, binding, cursor, closing) that this chain
performs invisibly.

**Answer.** `:city` is a **named bind parameter**: a placeholder in the SQL that will be filled by the
value registered under the name "city". It is exactly as injection-proof as `?`, because it is the
same mechanism — the SQL with the placeholder is parsed first, and the value arrives separately as
data, never passing through the parser; only the way the placeholder is located differs (by name
instead of by position). Invisibly, the chain: borrows a connection from the HikariCP pool (the old
`getConnection`), creates the prepared statement (`prepareStatement`), binds the value (the old
`setString`, now matched by name), executes the query (`executeQuery`), walks the cursor (the old
`while (rs.next())` loop) calling the mapper once per row, closes the `ResultSet` and the statement,
and returns the connection to the pool (the old `close()`), guaranteed even if an exception flies.

**Question 2.** The row mapper lambda `(rs, rowNum) -> new EventRow(...)` contains no loop and no
`rs.next()`. For a query that finds 4 rows: who moves the cursor from row to row, how many times does
the lambda run, and what would go wrong if you called `rs.next()` inside the lambda yourself?

**Answer.** `JdbcClient` moves the cursor — it runs the `rs.next()` loop internally. The lambda runs
once per row: four times here, each time with the cursor already standing on the row to map. Calling
`rs.next()` inside the lambda would advance the cursor mid-loop: the mapper would suddenly be reading
the *following* row's values, and when the internal loop then calls `rs.next()` itself, rows get
skipped — results silently vanish (and on the last row the mapper would be reading past the end, where
there is no valid data). The mapper's contract: read only the current row, never move the cursor.

### Your doubts

None recorded for this lesson.

---

## Lesson 16 — Side by side: what `JdbcClient` removed, and what it kept

### Definition

The same job — "find events in a city" — written twice: raw JDBC and `JdbcClient`, on one screen,
every job sorted into two piles: **removed** (done invisibly now) and **kept** (still yours). The
task wants the conclusion as one sentence (subtask 8); it is also checkpoint question 4.

### Why it is needed

Tools breed magical thinking. A developer who only ever saw `JdbcClient` believes the database layer
"just works" — until a log says `FATAL: password authentication failed`, or `request timed out
(total=10, active=10, idle=0)`, or `Bad value for type int`, and they cannot tell which layer is
talking. Knowing what the helper does **not** do tells you what you still own.

### Key points

- **Removed:** open connection, prepare statement, execute, walk the cursor, close in order, return
  to the pool. **Replaced:** positional `?` bookkeeping → named `.param(...)` (binding itself is
  still your job).
- **Kept:** the SQL, the schema knowledge inside it (tables, join condition, column names and types),
  parameter discipline (values through `:name`, never concatenation), the row mapping, and error
  triage.
- Subtle middle: the `ResultSet` still appears as `rs` inside the mapper — you still **use** it
  (read columns); you no longer **manage** it (loop, close).
- The one sentence: **`JdbcClient` removed the plumbing — opening, preparing, cursor-walking,
  closing, returning — but not the SQL, the schema, the duty to bind values as parameters, or the
  need to map rows and read errors.**

### Example — both programs (compiled and run for real)

**Raw JDBC** (plain Java file, driver jar on the classpath):

```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

// Raw JDBC, written by hand. Verbose on purpose — that is the point.
// city lives on venue, so reaching it from event takes a join (v.id = e.venue_id).
public class RawJdbc {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/zenith";
        String user = "postgres";
        String password = "dev";
        String city = args.length > 0 ? args[0] : "Hyderabad";

        String sql = "SELECT e.id, e.name FROM event e "
                   + "JOIN venue v ON v.id = e.venue_id "
                   + "WHERE v.city = ?";

        // Each numbered step is something JDBC made YOU do by hand.
        try (Connection c = DriverManager.getConnection(url, user, password);  // 1. open connection
             PreparedStatement ps = c.prepareStatement(sql)) {                 // 2. prepare statement
            ps.setString(1, city);                                             // 3. bind by position
            try (ResultSet rs = ps.executeQuery()) {                          // 4. execute
                System.out.println("Events in " + city + ":");
                while (rs.next()) {                                           // 5. step row by row
                    String id = rs.getString("id");                          // 6. read by column name
                    String name = rs.getString("name");                      // 6. read by column name
                    System.out.println("  " + id + "  " + name);
                }
            }                                                                 // 7. close rs
        }                                                                     // 7. close ps, then c
    }
}
```

```text
Events in Hyderabad:
  aaaaaaaa-0000-0000-0000-000000000006  Indie Fest
  aaaaaaaa-0000-0000-0000-000000000005  Sufi Night
  aaaaaaaa-0000-0000-0000-000000000002  Classical Evening
  aaaaaaaa-0000-0000-0000-000000000001  Monsoon Rock Night
```

**`JdbcClient`** (the runner inside the Spring Boot `zenith-app`; `pom.xml` has the JDBC starter and
Postgres driver, `application.yml` has url/username/password):

```java
    // One row of the result, as a Java object.
    record EventRow(UUID id, String name, String venue, OffsetDateTime startsAt) {}

    @Bean
    CommandLineRunner searchByCity(JdbcClient db) {
        return args -> {
            String city = args.length > 0 ? args[0] : "Hyderabad";

            List<EventRow> events = db.sql("""
                            SELECT e.id, e.name, v.name AS venue, e.starts_at
                            FROM event e
                            JOIN venue v ON v.id = e.venue_id
                            WHERE v.city = :city
                            ORDER BY e.starts_at
                            """)
                    .param("city", city)
                    .query((rs, rowNum) -> new EventRow(
                            rs.getObject("id", UUID.class),
                            rs.getString("name"),
                            rs.getString("venue"),
                            rs.getObject("starts_at", OffsetDateTime.class)))
                    .list();

            System.out.println(">>> events in " + city + ": " + events.size());
            for (EventRow e : events) {
                System.out.println(">>> " + e);
            }
        };
    }
```

```text
>>> events in Hyderabad: 4
>>> EventRow[id=aaaaaaaa-0000-0000-0000-000000000001, name=Monsoon Rock Night, venue=Riverside Arena, startsAt=2026-11-20T13:30Z]
>>> EventRow[id=aaaaaaaa-0000-0000-0000-000000000002, name=Classical Evening, venue=Riverside Arena, startsAt=2026-12-05T13:00Z]
>>> EventRow[id=aaaaaaaa-0000-0000-0000-000000000005, name=Sufi Night, venue=Riverside Arena, startsAt=2027-07-10T14:00Z]
>>> EventRow[id=aaaaaaaa-0000-0000-0000-000000000006, name=Indie Fest, venue=Riverside Arena, startsAt=2027-07-24T11:30Z]
```

### Walkthrough — the count

| # | Raw JDBC job | In the `JdbcClient` version |
| --- | --- | --- |
| 1 | Open connection (`DriverManager.getConnection`) | **Removed** — borrowed from the HikariCP pool, invisibly |
| 2 | Prepare statement (`prepareStatement`) | **Removed** — done inside `.sql(...)` |
| 3 | Bind by position (`setString(1, city)`) | **Replaced** — `.param("city", city)`, by name; binding still yours |
| 4 | Execute (`executeQuery`) | **Removed** — triggered by `.list()` |
| 5 | Step the cursor (`while (rs.next())`) | **Removed** — `JdbcClient` loops, calls your mapper per row |
| 6 | Read columns from the row | **Kept** — that *is* the row mapper, now producing a record |
| 7 | Close rs, ps, c in order | **Removed** — closed and returned automatically, exception or not |

What never left your desk:

- **The SQL.** The raw program's rows came back in arbitrary order; the `JdbcClient` rows came back
  sorted — only because *that* SQL says `ORDER BY e.starts_at`. The tool runs your SQL; it does not
  improve it.
- **The schema.** Tables, the join condition, column names and types — the `uuid`-read-as-`int`
  crash proved types are still your problem.
- **Parameter discipline.** `db.sql("... WHERE v.city = '" + city + "'")` compiles, runs, and is
  injectable, exactly as before. Protection exists only because you route values through `:city`.
- **Error triage.** `FATAL`, `Connection refused`, pool timeouts, type mismatches — same messages,
  same layers, same detective work.

### Important terms

| Term | Meaning |
| --- | --- |
| **Plumbing** | The removable jobs: open, prepare, execute, loop, close, return. |
| **Using vs managing the `ResultSet`** | Mapper reads columns (using); `JdbcClient` loops and closes (managing). |
| **The one sentence (subtask 8)** | Removed the plumbing; kept the SQL, schema, binding discipline, mapping, and error reading. |

### Common mistakes

- **Thinking `JdbcClient` writes SQL for you.** It does not generate or check SQL — typos are found by
  Postgres at run time, not by Java at compile time.
- **Thinking it makes injection impossible.** It makes safe binding easy, not automatic; concatenation
  into `.sql(...)` is as dangerous as ever.
- **Thinking try-with-resources knowledge is obsolete.** It is what runs underneath — and direct
  `DataSource.getConnection()` use puts you back on manual duty.
- **Thinking the layers below are gone.** Any stack trace shows the tower: Spring's helpers, HikariCP,
  `org.postgresql` — every layer this task met.

### Where it is used

Daily team reasoning: write queries with the helper, debug with the raw-JDBC mental model. When a log
says `active=10, idle=0`, the person who wrote steps 1–7 by hand knows which layer is screaming.

### Link to the task

Subtask 8 done (the one sentence, to rephrase in your own words) and checkpoint question 4 answered.

### Do it yourself — write the one sentence (blank laptop)

No new code; both programs already run on your laptop from earlier lessons.

1. Open `RawJdbc.java` and `ZenithApp.java` side by side.
2. For each numbered comment (1–7) in the raw file, find where it lives in the `JdbcClient` chain —
   or say "gone, done for me".
3. Write one sentence in your own words: what did `JdbcClient` remove, what did it keep? Put it in
   your notes. (Model: "It removed the connection/statement/cursor/closing plumbing, but kept the
   SQL, the schema, the parameter binding, and the row mapping as my job.")
4. Prove the "kept" pile: delete `ORDER BY e.starts_at` from the `JdbcClient` SQL, rerun, watch the
   order become arbitrary — your SQL did the sorting, not the tool. Put the line back.

**Done when:** you can name from memory at least four removed jobs and four kept jobs, and say the
sentence without looking.

### Summary for revision

- Removed: open, prepare, execute, cursor loop, close/return. Replaced: positions → names.
- Kept: SQL, schema (names + types), parameter discipline, row mapping, error triage.
- `rs` in the mapper = using, not managing.
- The helper runs your SQL; it never improves, checks, or secures it for you.

### Check questions, with answers

**Question 1.** A teammate sees this chain and says: "Great — with `JdbcClient` I no longer need to
know JDBC or SQL internals."

```java
List<EventRow> events = db.sql("SELECT e.id, e.name FROM event e JOIN venue v ON v.id = e.venue_id WHERE v.city = :city")
        .param("city", city)
        .query((rs, rowNum) -> new EventRow(rs.getObject("id", UUID.class), rs.getString("name")))
        .list();
```

List what this chain truly does for them — and at least four things it still expects them to know or
do correctly, with what goes wrong in each case if they don't.

**Answer.** The chain does the plumbing: borrows a connection from the pool and always returns it
(even on exception), prepares the statement, binds the named value, executes, walks the cursor calling
the mapper once per row, closes the `ResultSet` and statement, and collects the list. Still theirs:
(1) **write correct SQL** — a typo or wrong join is caught by Postgres at run time as an error or, worse,
runs and returns wrong rows; (2) **know the schema** — a wrong column name fails at run time, and a
wrong type read fails like `Bad value for type int : aaaaaaaa-...` (reading a `uuid` with `getInt`);
(3) **bind values through `:name`, never concatenation** — gluing user input into `.sql(...)` reopens
SQL injection exactly as in raw JDBC; (4) **supply every named parameter** — a missing or misspelled
`.param` fails with `No value supplied for the SQL parameter 'city'`; (5) **read errors across
layers** — `FATAL: password authentication failed` (server rejected credentials), `Connection ...
refused` (nothing listening), `request timed out (total=10, active=10, idle=0)` (pool empty: leak or
undersized pool).

**Question 2.** Inside the mapper there is still an `rs`:
`(rs, rowNum) -> new EventRow(rs.getObject("id", UUID.class), ...)`. Does that mean the `ResultSet`
"came back" as our responsibility? Explain the difference between *using* the `ResultSet` and
*managing* it, and say who loops it and who closes it now. Then: is this variant safe, and why or why
not — `db.sql("SELECT e.id, e.name FROM event e JOIN venue v ON v.id = e.venue_id WHERE v.city = '" + city + "'")`?

**Answer.** No — only the *reading* came back, not the responsibility. **Using** the `ResultSet` means
reading the current row's columns (`getObject`, `getString`); that is all the mapper does. **Managing**
it means moving the cursor (`rs.next()`), closing it, closing the statement, and returning the
connection — all of that is `JdbcClient`'s job now: it loops, it closes, it returns, even when an
exception flies. The concatenated variant is **not safe**: the city value is glued into the SQL text
before parsing, so the parser reads attacker-controlled characters, and inputs like `' OR '1'='1`
become SQL again. `JdbcClient`'s protection is parse-then-bind, and it exists only when the value
travels through a parameter (`:city` plus `.param`). Concatenation compiles and runs — and is exactly
as injectable as it was in raw JDBC.

### Your doubts

None recorded for this lesson.

---

## Lesson 17 — The deep answer: why binding cannot be injected, and the three ways people get injected anyway

### Definition

The task's final subtask is a question for the AI: *"Explain exactly why a PreparedStatement with a
bind parameter cannot be SQL-injected, at the level of what the database does with the statement and
the value separately. Then show me three ways developers still manage to create injection
vulnerabilities even while using prepared statements."* This lesson is the full answer, with all three
ways run for real, plus the four checkpoint answers in one place.

### Why it is needed

"We use prepared statements" has appeared in the post-mortem of real breaches — true, and injected
anyway. Spotting the escape hatches needs the mechanism one level deeper than "the value is treated as
data".

### The mechanism — what the database does, exactly

A `PreparedStatement` makes the driver and Postgres exchange **three separate messages**:

1. **Parse.** The SQL text travels with the placeholder still in it (the driver converts `?` to
   Postgres's `$1`). The parser builds the statement's internal structure — "a SELECT, joining these
   tables, comparing `v.city` with one incoming text value" — and finishes. The structure is frozen.
   No value exists yet; only an empty slot.
2. **Bind.** The value travels in a second message, tagged as *data for slot 1*. It is attached to the
   frozen structure. The parser never sees this message — it already finished, and this envelope is
   never opened by it.
3. **Execute.** The frozen structure runs; the slot's data is compared against the column; rows come
   back.

**The SQL text and the value travel in different messages, and only the first ever meets the parser.**
The value is never pasted into SQL text, so there is nothing to escape and no quote to break out of.
Concatenation instead sends one message whose text already contains the attacker's characters — and
reading text as SQL is the parser's job. The immunity is timing and separation: parsed first, value
later, never re-parsed.

### Example — the three ways it still goes wrong (all run for real)

Setup for Way 3: a **stored function** — SQL code saved inside the database — that builds a query by
gluing text and runs it with `EXECUTE` (the vulnerable pattern):

```sql
CREATE OR REPLACE FUNCTION events_in(p_city text)
RETURNS TABLE(name text) LANGUAGE plpgsql AS $$
BEGIN
  RETURN QUERY EXECUTE
    'SELECT e.name FROM event e JOIN venue v ON v.id = e.venue_id WHERE v.city = ''' || p_city || '''';
END $$;
```

The Java program (tables: `venue(name, city, capacity)`, `event(id, venue_id, name, starts_at)`;
database `zenith`: 3 venues, 7 events, 4 in Hyderabad):

```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

// Three ways injection survives even though "we use prepared statements".
public class StillInjectableDemo {
    static final String URL  = "jdbc:postgresql://localhost:5432/zenith";
    static final String USER = "postgres";
    static final String PASS = "dev";

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, USER, PASS)) {
            partA_identifierConcat(c);
            partB_gluedThenPrepared(c);
            partC_serverSideGlue(c);
        }
    }

    // Way 1: the ORDER BY column is glued, because ? cannot stand for a column name.
    static void partA_identifierConcat(Connection c) throws Exception {
        System.out.println("=== Way 1: concatenated ORDER BY (identifier) ===");
        for (String sortColumn : new String[]{"name", "capacity DESC"}) {
            String sql = "SELECT name, capacity FROM venue ORDER BY " + sortColumn;   // glued!
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                System.out.println("user input = \"" + sortColumn + "\" ->");
                while (rs.next()) {
                    System.out.println("   " + rs.getString("name")
                            + "  (capacity " + rs.getInt("capacity") + ")");
                }
            }
        }
        System.out.println("(\"capacity DESC\" was user input and it RAN AS SQL - the keyword steered the query)");
    }

    // Way 2: the string was glued BEFORE prepareStatement - preparing it changes nothing.
    static void partB_gluedThenPrepared(Connection c) throws Exception {
        System.out.println();
        System.out.println("=== Way 2: glued first, prepared after ===");
        for (String city : new String[]{"Hyderabad", "' OR '1'='1"}) {
            String sql = "SELECT count(*) FROM event e JOIN venue v ON v.id = e.venue_id "
                       + "WHERE v.city = '" + city + "'";            // glued BEFORE preparing
            try (PreparedStatement ps = c.prepareStatement(sql);     // "prepared" - but too late
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                System.out.println("city input = \"" + city + "\"  ->  rows matched: " + rs.getInt(1));
            }
        }
    }

    // Way 3: Java binds perfectly - but a function INSIDE the database glues the value into new SQL.
    static void partC_serverSideGlue(Connection c) throws Exception {
        System.out.println();
        System.out.println("=== Way 3: Java binds perfectly, the database function glues ===");
        String sql = "SELECT * FROM events_in(?)";                   // a real bind parameter!
        for (String city : new String[]{"Hyderabad", "' OR '1'='1"}) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, city);                               // bound as data - safe so far
                try (ResultSet rs = ps.executeQuery()) {
                    int n = 0;
                    while (rs.next()) n++;
                    System.out.println("city input = \"" + city + "\"  ->  rows returned: " + n);
                }
            }
        }
        System.out.println("(the function inside Postgres glued the value into new SQL - injection moved server-side)");
    }
}
```

Real output:

```text
=== Way 1: concatenated ORDER BY (identifier) ===
user input = "name" ->
   Lakeside Hall  (capacity 800)
   Riverside Arena  (capacity 12000)
   The Blue Note  (capacity 350)
user input = "capacity DESC" ->
   Riverside Arena  (capacity 12000)
   Lakeside Hall  (capacity 800)
   The Blue Note  (capacity 350)
("capacity DESC" was user input and it RAN AS SQL - the keyword steered the query)

=== Way 2: glued first, prepared after ===
city input = "Hyderabad"  ->  rows matched: 4
city input = "' OR '1'='1"  ->  rows matched: 7

=== Way 3: Java binds perfectly, the database function glues ===
city input = "Hyderabad"  ->  rows returned: 4
city input = "' OR '1'='1"  ->  rows returned: 7
(the function inside Postgres glued the value into new SQL - injection moved server-side)
```

### Walkthrough — the three ways, decoded

1. **Identifiers.** A `?` can only stand for a **value**, never a column name, table name, or keyword —
   the parser needs those to build the structure. So developers glue them: `"ORDER BY " + sortColumn`.
   The user's `capacity DESC` ran as SQL; the same slot swallows data-leaking subqueries.
   **Fix: allow-list** — a fixed set of permitted column names in code; validate, then use the
   validated name; reject everything else.
2. **Glued first, prepared after.** The text was assembled with the value inside, then handed to
   `prepareStatement` — the attack is already in the Parse message; binding never happens. All 7 rows
   came back, same as the worst concatenation, because it *is* concatenation wearing a seatbelt.
   Includes the half-and-half version (three values bound, one glued "just this once" — one is
   enough). **Fix: every value through a placeholder, no exceptions.**
3. **Server-side glue / second-order.** Java bound perfectly; the value crossed the network as data.
   Then the function inside Postgres built new SQL text with `||` and ran it with `EXECUTE` — the
   parser met the attacker's quotes in a second round, inside the database. Same trap without
   functions: store a value safely today, glue it into a report query next month ("second-order
   injection"). **Fix: the rule is per-query, forever** — any code building SQL text from runtime
   values is suspect, wherever the values came from. (Inside Postgres functions the safe form is
   `EXECUTE ... USING` — server-side binding.)

Clean up the deliberately vulnerable function when done:

```bash
docker exec pg psql -U postgres -d zenith -c "DROP FUNCTION events_in(text);"
```

### The four checkpoint answers, in one place

**1. Why is a `PreparedStatement` immune to injection, in terms of what the database actually does?**
The SQL text and the value travel in separate messages. The text (with the placeholder) arrives first
and is parsed into a frozen structure with an empty slot. The value arrives second, tagged as data for
that slot, and is never shown to the parser. Since the value is never part of any SQL text, its
characters can never become commands — nothing to escape, no quote to break out of.

**2. What does try-with-resources protect you from, and what happens without it?**
It protects from resource leaks: it closes the connection, statement, and result set automatically
when the block ends — in reverse order, even when an exception flies or a `return` leaves early.
Without it, error paths skip the close lines and occupied connections pile up. Without a pool, the
server's ~100 `max_connections` slots fill and Postgres refuses everyone: `FATAL: sorry, too many
clients already`. With a pool, the 10 pooled connections run out and every borrower waits, then dies
with `Connection is not available, request timed out (total=10, active=10, idle=0)` — and until that
timeout, everything hangs quietly.

**3. What is a connection pool for, and what does its size limit?**
Opening a connection is expensive (network hello, password check, session setup — ~195 ms cold); a
pool pays that cost once, keeps connections open, and lends them out in microseconds. Its maximum size
(HikariCP default: **10**) is a hard ceiling on connections held at the same moment — equal to how many
queries the app can have in flight at once, and how much load it can put on Postgres.

**4. What does `JdbcClient` remove compared with raw JDBC, and what does it deliberately keep?**
Removes the plumbing: open, prepare, execute, cursor-walk, close in order, return to the pool (and
swaps positional `?` for named `:city`). Deliberately keeps **the SQL in your hands** — you always know
exactly what runs — plus the schema knowledge, the duty to bind values, the row mapping, and the error
reading. That is why hot paths use `JdbcClient` rather than an ORM.

**Done when — met in full:** a successful injection ran against our own code (all 7 rows; a table
dropped), was defeated with a bind parameter (same inputs, 0 rows, table intact), and the same data was
read through both raw JDBC and `JdbcClient` (4 Hyderabad, 3 Mumbai, both ways).

### Common mistakes

- **Treating "we use prepared statements" as a project property.** It is a per-query property. One
  glued `ORDER BY` in one forgotten endpoint is a breach.
- **Trusting a value because it was stored safely.** Binding protects the query it was bound into, not
  the value's future. Stored-then-glued is injectable.
- **Forgetting the database can glue too.** Review stored functions for `||` + `EXECUTE` exactly like
  Java for `+` in SQL strings.
- **Escaping instead of binding.** Hand-escaping loses against encodings and edge cases; binding
  removes the problem instead of relocating it.

### Link to the task

Subtask 10 done — and with it all ten subtasks of Task F5.10, all four checkpoint questions, and the
"done when" line.

### Do it yourself — run all three escape hatches (blank laptop)

Postgres running and seeded; driver jar next to your `.java` files.

1. Create the vulnerable function:

   ```bash
   docker exec pg psql -U postgres -d zenith -c "CREATE OR REPLACE FUNCTION events_in(p_city text) RETURNS TABLE(name text) LANGUAGE plpgsql AS \$\$ BEGIN RETURN QUERY EXECUTE 'SELECT e.name FROM event e JOIN venue v ON v.id = e.venue_id WHERE v.city = ''' || p_city || ''''; END \$\$;"
   ```

   Expected: `CREATE FUNCTION`.
2. Create `StillInjectableDemo.java` with the code from the Example.
3. Compile and run (Mac/Linux; Windows uses `;` in `-cp`):

   ```bash
   javac -cp .:postgresql-42.7.4.jar StillInjectableDemo.java
   java -cp .:postgresql-42.7.4.jar StillInjectableDemo
   ```

   Expected: Way 1 reorders venues when the input is `capacity DESC`; Ways 2 and 3 both show
   Hyderabad → 4 but the attack string → 7.
4. Clean up: `docker exec pg psql -U postgres -d zenith -c "DROP FUNCTION events_in(text);"` →
   `DROP FUNCTION`.
5. Optional: paste the task's AI question into any AI and compare its answer with these notes.

**Done when:** you can explain Parse and Bind as two separate messages, and name the three escape
hatches (identifiers, glued-then-prepared, server-side glue) with the fix for each.

### Summary for revision

- Immunity = timing and separation: Parse (text with placeholder, structure frozen) → Bind (value as
  tagged data, parser never sees it) → Execute. Never re-parsed, so data cannot become SQL.
- Escape hatch 1: identifiers cannot be `?` — glued `ORDER BY`/column/table names; fix = allow-list.
- Escape hatch 2: glue first, prepare after (including one glued value among bound ones); fix = every
  value through a placeholder.
- Escape hatch 3: server-side glue (`||` + `EXECUTE` in functions) and second-order (stored safely,
  glued later); fix = per-query discipline forever, `EXECUTE ... USING` inside functions.

### Check questions, with answers

**Question 1.** Three code snippets search events by a user-supplied city or sort venues by a
user-supplied column. Which one(s) are injectable, why, and what is the fix?

```java
// (i)
PreparedStatement ps = c.prepareStatement(
    "SELECT e.name FROM event e JOIN venue v ON v.id = e.venue_id WHERE v.city = ?");
ps.setString(1, city);

// (ii)
PreparedStatement ps = c.prepareStatement(
    "SELECT name, capacity FROM venue ORDER BY " + sortColumn);

// (iii)
db.sql("SELECT e.name FROM event e JOIN venue v ON v.id = e.venue_id WHERE v.city = :city")
  .param("city", city)
  .query((rs, i) -> rs.getString("name"))
  .list();
```

**Answer.** (i) is safe: the city travels through a bind parameter — the SQL with `?` is parsed first,
the value arrives in a separate message as data and never meets the parser. (iii) is safe for the same
reason: `:city` is a named bind parameter, same parse-then-bind mechanism, located by name instead of
position. (ii) is injectable: `sortColumn` is an identifier glued into the SQL text before parsing, so
the user's characters are inside the Parse message and run as SQL; `prepareStatement` cannot help
because the dangerous text is already in the statement, and `?` could not stand for a column name
anyway. Fix: an allow-list — keep a fixed set of permitted column names in code, check the input
against it, use only the validated name, reject everything else.

**Question 2.** A feedback form saves user text with a perfectly bound INSERT
(`INSERT INTO feedback (text) VALUES (?)` plus `setString`). A month later, a reporting job runs:
`"SELECT count(*) FROM feedback WHERE text = '" + storedText + "'"`, where `storedText` was read back
from that table. A teammate says: "That value came from our own database and was inserted with a bind
parameter, so it is clean." Is the reporting query safe?

**Answer.** Not safe. Injection protection applies **per query, at that query's parse time**: a value
is data only while it travels through a bind parameter of the query being run. The bound INSERT
protected the INSERT — nothing more; it faithfully stored whatever the user typed, including something
like `' OR '1'='1` or `'; DROP TABLE feedback; --`. The reporting query glues `storedText` into its
own SQL text, so *its* parser reads those stored characters as SQL — this is second-order injection:
stored safely, exploded later. Where a value came from grants no safety; only how the current query
carries it does. Fix: the reporting query must bind too —
`"SELECT count(*) FROM feedback WHERE text = ?"` with `setString`.

### Your doubts

None recorded for this lesson.

---

# F5.1 — Run Postgres in Docker and look around

Notes for Task F5.1. One lesson per section. Each lesson ends with its understanding-check
questions and full answers.

---

## Lesson 1 — What a database server is

### Definition

A **database server** is a program that runs all the time, holds data, and answers questions
about that data. Other programs send it questions and get answers back.

### Why it is needed

Without one, your program saves data by writing plain files. Three problems follow:

- **Two programs write at once.** Your app and a report script write to the same file at the
  same time. The file gets corrupted. Neither program notices.
- **Finding data is slow.** You want "all orders from March". You must read the whole file,
  line by line, every time.
- **A crash loses data.** Your program dies halfway through a write. The file holds half a
  record. Nobody knows which half.

A database server solves all three. It is the *only* program that touches the stored data.
Everyone else must ask it. Because it sits in the middle, it can take turns between writers,
find data fast, and survive crashes without losing records.

**Analogy.** A library with no librarian is a pile of books. Anyone can grab, move, or tear a
book, and nobody can find anything. A librarian fixes this: you ask the librarian, and the
librarian fetches the book, records who has it, and keeps the shelves in order. The database
server is the librarian. The data is the books.

### How it works, step by step

1. **The server starts and keeps running.** It is a long-running program, like a web server.
   It does not run once and exit. In this task it runs inside a Docker container.
2. **The server listens on a port.** A **port** is a numbered door on a machine. Programs each
   listen behind their own door number. Postgres listens on door **5432** by convention.
3. **A client connects and talks to it.** A **client** is any program that connects to the
   server, sends a question, and reads the answer. Questions are written in a language called
   **SQL**. A Java app is a client. The terminal tool `pgcli` is also a client.

The flow is always: client connects to the server's port, client sends an SQL question, server
computes the answer from its stored data, server sends the answer back.

### Example

The smallest possible conversation with a database server:

```sql
SELECT now();
```

This one SQL question means: "tell me the current date and time." The server answers:

```text
              now
-------------------------------
 2026-10-24 01:10:42.183+05:30
```

Walkthrough:

1. The client is already connected to the server's port. The connection is an open phone line.
2. The client sends the text `SELECT now();` down that line.
3. The server reads it and understands: the client wants the current time.
4. The server looks at its own clock and builds a one-row answer.
5. The server sends the answer back down the same line.
6. The client prints the answer on the screen.

The client did none of the work. It only asked and displayed. All thinking happened inside the
server. This split never changes, whether the question is "what time is it" or "sum all
payments from March".

### Important terms

| Term | Meaning |
| --- | --- |
| **Database server** | The long-running program that stores data and answers questions. |
| **Port** | A numbered door on a machine. Each listening program has its own number. |
| **Client** | Any program that connects to the server, asks, and reads the answer. |
| **SQL** | The language clients use to ask a database server questions. |

### Common mistakes

- **Thinking "Postgres" is a file you open.** It is a running program. If the program is not
  running, there is nothing to connect to, no matter what files exist on disk.
- **Thinking the terminal tool is the database.** `pgcli` holds no data. It is only a phone
  used to call the server. Closing `pgcli` loses nothing. Stopping the server is different.
- **Confusing the server with a database.** The server is the running program. A **database**
  is one named collection of data *inside* that server. One server can hold many databases.

### Where it is used

Almost every app sits on a database server. A bank stores accounts in one. A shop stores
orders in one. A chat app stores messages in one. Many app servers act as clients of one
database server, and the database server guards the data.

### Link to the task

Subtask 1 of F5.1 starts Postgres with `docker run`. The thing that command starts is exactly
this — a database server. Every later subtask (connecting, creating a database, breaking
passwords and ports) is a client talking to this server, or failing to.

### Summary for revision

- A database server is a long-running program that guards data and answers SQL questions.
- It listens on a port (Postgres: 5432). Clients connect to that port and ask.
- The client only asks and displays. The server does all the work.
- Server ≠ database: the server is the program; a database is a named collection inside it.

### Check questions, with answers

**Question 1.** Your friend says: "I installed Postgres, so my data is safe in the Postgres
file. I don't need anything running." What is wrong with this sentence?

**Answer.** Postgres is not a file; it is a program that must be running. Installing it only
puts the program on disk. If the server program is not running, no client can connect, no
question can be asked, and no data can be read or written. The data does live in files on
disk, but only the running server may touch those files safely.

**Question 2.** A machine runs one Postgres database server. Three programs talk to it: a Java
web app, a Python report script, and a person typing in `pgcli`. For each of the four programs
named, say whether it is a server or a client.

**Answer.**

| Program | Role |
| --- | --- |
| Postgres | Server. It holds the data and answers questions. |
| Java web app | Client. It connects and asks questions. |
| Python report script | Client. It connects and asks questions. |
| `pgcli` | Client. It is a terminal tool a person types questions into. |

One server, many clients. Anything that connects and asks is a client, whether it is code or
a person at a terminal.

### Your doubts

None for this lesson.

---

## Lesson 2 — What lives inside a server: database, schema, table, row, column

### Definition

A database server stores data in a strict hierarchy of named boxes. From biggest to smallest:
the **server** holds **databases**, a database holds **schemas**, a schema holds **tables**,
and a table holds **rows** made of **columns**.

### Why it is needed

If the server had no boxes inside, all data would sit in one giant pile. Three problems:

- **Different projects collide.** A shop app and a blog app both want to store something
  called `users`. In one pile, they would overwrite each other.
- **Nothing can be found by name.** You could not say "show me the orders". There is no thing
  called "orders". There is only the pile.
- **Nothing checks the data's shape.** One order record has a price. The next has the price
  missing, or text where a number should be. Nothing stops this.

The hierarchy solves all three. Databases keep projects apart. Tables give each kind of record
a name and a fixed shape. Columns force every record to have the same fields with fixed types.

**Analogy.** The server is a filing cabinet building. Each **database** is one cabinet. Each
**schema** is one drawer. Each **table** is one labelled folder in a drawer. Each **row** is
one sheet of paper in the folder. Each **column** is one blank printed on every sheet, like
"Name:" or "Date:". Every sheet in a folder has the same blanks.

### How it works, step by step

1. **Server.** The running Postgres program. One per container in this task.
2. **Database.** One named collection inside the server. The server starts with a built-in
   database named `postgres`. You will create your own named `zenith`. Databases are walls:
   a query runs inside one database and cannot see the others.
3. **Schema.** A named folder of tables inside a database. Every database has a default schema
   named `public`. Everything you create goes into `public` unless you say otherwise.
4. **Table.** A grid of data with a name, like `event`. It has named columns and many rows.
5. **Column.** One named field with a **type**. A type says what values are allowed: whole
   numbers, text, dates, and so on. Every row must satisfy every column's type.
6. **Row.** One record. One event, one seat, one order. A row has one value per column.

**Warning: the word "schema" has two meanings.** Meaning one: a named folder of tables, like
`public`. Meaning two: the design of all your tables — which tables exist and what columns
they have. People say "design the schema" in the second sense. Tell them apart from context.

### Example

One small table named `event`, with three columns and two rows:

| id | name | starts_at |
|----|-------------------|---------------------|
| 1  | Spring Jazz Night | 2026-11-02 19:00:00 |
| 2  | Tech Meetup       | 2026-11-05 18:30:00 |

Columns with types: `id` is `integer` (whole number), `name` is `text`, `starts_at` is
`timestamp` (date and time).

Walkthrough of the full hierarchy, top to bottom:

1. The **server** is running. It holds every database.
2. Inside the server there is a **database** named `zenith`.
3. Inside `zenith` there is the default **schema** named `public`.
4. Inside `public` there is a **table** named `event`.
5. The table has three **columns**: `id` (integer), `name` (text), `starts_at` (timestamp).
6. The table has two **rows**: one record per event.

Testing the walls: storing the text `"hello"` in `id` fails, because `id` only accepts whole
numbers. Reading `event` while connected to the `postgres` database fails, because databases
cannot see into each other.

### Important terms

| Term | Meaning |
| --- | --- |
| **Database** | One named collection of tables inside a server. One server holds many. |
| **Schema** | A named folder of tables inside a database. Default: `public`. |
| **Table** | A grid of data with named, typed columns. |
| **Row** | One record in a table. |
| **Column** | One named field with a type every row must satisfy. |
| **Type** | The rule for what values a column accepts (integer, text, timestamp, ...). |

### Common mistakes

- **Mixing up database and table.** A database is the big box for a whole project. A table is
  one grid inside it, for one kind of record.
- **Expecting rows to stay in order.** A table is a bag of rows, not a list. The server may
  return rows in any order unless the query asks for a specific order.
- **Forgetting that "schema" is overloaded.** Folder of tables, or design of tables? Check
  which meaning the speaker intends.

### Where it is used

Every real system maps its things to tables. A ticketing system: `event`, `seat`, and
`booking` tables in one database. A bank: `account` and `transfer` tables. Designing a schema
(meaning two) is deciding which tables to make and which columns each gets.

### Link to the task

`\l` lists the **databases** in the server. `\dt` lists the **tables** in the current
database's `public` **schema**. `\d event` describes one table: its **columns** and types.
`CREATE DATABASE zenith;` creates a new database — a new empty cabinet.

### Summary for revision

- Hierarchy, big to small: server → database → schema → table → row/column.
- Default schema is `public`; it is the only schema used in this module.
- Columns have types; every row must satisfy them.
- Databases are walls: one query sees one database only.
- "Schema" also means "the design of the tables" — context decides.

### Check questions, with answers

**Question 1.** Here is a hierarchy, scrambled: `row`, `server`, `column`, `database`,
`table`, `schema`. Put the six words in order from biggest container to smallest part. Then
say which one is "a named folder of tables with the default name `public`".

**Answer.** Biggest to smallest: server → database → schema → table → row → column. (A row is
made of column values, so column is the smallest part.) The "named folder of tables with the
default name `public`" is the **schema**.

**Question 2.** A Postgres server holds two databases: `shop` and `blog`. Both contain a table
named `users` in their `public` schema. You are connected to the `blog` database and ask for
rows from `users`. Which table answers — `shop`'s, `blog`'s, or both?

**Answer.** Only `blog`'s `users` table answers. Databases are walls: a connection lives
inside one database and cannot see tables in any other. The same table name can exist in many
databases without conflict, because each database is a separate box.

### Your doubts

None for this lesson.

---

## Lesson 3 — Four Docker words: image, container, port mapping, environment variable

Glossary depth only. Docker is taught properly in Task F7.8. This is just enough to read one
`docker run` command without guessing.

### Definition

An **image** is a frozen template of a program and everything it needs. A **container** is one
running copy made from an image. A **port mapping** connects a door on your machine to a door
inside the container. An **environment variable** is a named setting, `KEY=value`, that a
program reads when it starts.

### Why it is needed

Without Docker, installing Postgres means downloading the right version, running an installer,
hoping it does not clash with other installs, and struggling to remove it later. Docker fixes
this: someone packages Postgres once as an image; you run it as a sealed-off container; delete
it and your machine is exactly as before.

The seal creates two needs. Your machine cannot reach into a sealed box, so you open a door on
purpose — the port mapping. A sealed box cannot ask setup questions, so you hand it settings
up front — environment variables.

### How it works, step by step

1. **Image.** A frozen template: the Postgres program, the operating system files it needs,
   default settings. Does nothing by itself. Named `name:tag` — `postgres:17-alpine` means
   Postgres version 17 built on Alpine, a tiny operating system that keeps the download small.
2. **Container.** One running copy made from an image. Like cooking one meal from a recipe:
   the recipe is not used up, and one image can start many containers. Each container has its
   own name and data, and can be stopped, started again, or removed.
3. **Port mapping.** A port is a numbered door on a machine; Postgres listens on door 5432.
   A container's door is inside the seal. `-p 5432:5432` says: forward my machine's door 5432
   (left number) to the container's door 5432 (right number). Without it, nothing on the
   machine can reach the Postgres inside.
4. **Environment variable.** `-e KEY=value`, passed at start. The Postgres image reads
   `POSTGRES_PASSWORD` and uses it as the superuser password. No installer dialog appears —
   the variable is the answer, given in advance.

### Example

```text
Image:      postgres:17-alpine          (frozen template, does nothing)
Container:  pg                          (running copy, made from that image)
Mapping:    Mac door 5432  →  container door 5432
Setting:    POSTGRES_PASSWORD=dev       (read once, at first start)
```

Walkthrough of a container starting from `postgres:17-alpine`:

1. Docker checks if the image is downloaded. If not, it downloads it first.
2. Docker creates a sealed box and copies the image's contents in as the starting state.
3. Docker passes in `POSTGRES_PASSWORD=dev`. Postgres reads it and sets the superuser
   password to `dev`.
4. Docker connects the Mac's door 5432 to the container's door 5432.
5. Postgres starts inside the box and listens on its door 5432.
6. A client knocking on `localhost:5432` is forwarded through the mapping to Postgres.

One image could start a second container with a different name and different mapped door —
a separate server with separate data.

### Important terms

| Term | Meaning |
| --- | --- |
| **Image** | Frozen template of a program and all it needs. Named `name:tag`. |
| **Container** | One running copy made from an image. Stop, start, remove. |
| **Port mapping** | `-p host:container` — forwards a door on your machine to one inside. |
| **Environment variable** | `-e KEY=value` — a setting handed to the program at start. |
| **Tag** | The version part of an image name, after the colon. |

### Common mistakes

- **Saying "image" when you mean "container".** You stop, start, and remove containers. You
  download images. If it runs, it is a container. If it is a template, it is an image.
- **Reading `-p 5432:5432` as one number.** Two numbers: left is your machine's door, right is
  the container's. `-p 5433:5432` is valid; clients would then connect to 5433.
- **Expecting a password prompt.** It never asks. The environment variable is the answer.

### Where it is used

Backend teams run databases, caches, and message queues in containers during development, and
production systems run containers too. Every module in this course from here on starts its
database with `docker run`.

### Link to the task

Subtask 1 runs:

```bash
docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 postgres:17-alpine
```

`postgres:17-alpine` is the **image**, `--name pg` names the **container**, `-p 5432:5432` is
the **port mapping**, `-e POSTGRES_PASSWORD=dev` is the **environment variable**.

### Summary for revision

- Image = recipe. Container = one cooked meal. Many containers can come from one image.
- `-p host:container` opens a door through the seal. Left number is yours.
- `-e POSTGRES_PASSWORD=dev` answers the setup question before it is asked.
- Download images; stop/start/remove containers.

### Check questions, with answers

**Question 1.** Your friend says "I stopped the image and removed the image, so the database
is gone." Both uses of the word "image" are wrong. What word should your friend have used, and
what is the difference between the two words?

**Answer.** The friend means **container**. The container is the running copy — it is what you
stop and remove. The image is the frozen template it was made from; removing a container does
not touch the image, and the image alone holds no live database.

**Question 2.** A container is started with the mapping `-p 5433:5432`. Postgres inside the
container listens on its usual door, 5432. A client on your Mac wants to connect. Which door
must the client knock on: 5432 or 5433?

**Answer.** 5433. The left number of the mapping is the door on your machine, and that is the
only door a client on your machine can see; Docker forwards knocks on 5433 to the container's
inside door 5432.

### Your doubts

None for this lesson.

---

## Lesson 4 — The `docker run` command, part by part

### Definition

`docker run` is the command that creates a new container from an image and starts it. Every
flag before the image name is a setting for the new container.

### Why it is needed

A container cannot be asked questions after it is sealed. Every decision must be given at
creation time: what to call it, which doors to open, which settings to hand in, which image to
build from. `docker run` states all those decisions in one line.

The pain of not understanding it: you copy the line from a tutorial, it works, and one day it
fails or connects to nothing. If each part is a mystery, you cannot tell which part to fix.

### How it works, step by step

The shape: `docker run [flags] image:tag`. The task's command, read left to right:

```bash
docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 postgres:17-alpine
```

1. `docker run` — create a new container and start it.
2. `-d` — **detached**: run in the background and give the terminal back. Without it, the
   terminal stays attached to the container's log output until Ctrl-C.
3. `--name pg` — call the container `pg`. Without a name Docker invents a random one like
   `nervous_tesla`. A fixed name lets later commands say `docker logs pg`.
4. `-e POSTGRES_PASSWORD=dev` — an environment variable. The Postgres image reads
   `POSTGRES_PASSWORD` and sets the superuser's password to `dev`. The **superuser** is the
   account named `postgres` that can do anything in the server.
5. `-p 5432:5432` — port mapping. Left number: the door on this Mac. Right number: the door
   inside the container where Postgres listens.
6. `postgres:17-alpine` — the image, always last. Everything before it is a flag.

### Example — real run on this machine

```text
$ docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 postgres:17-alpine
Unable to find image 'postgres:17-alpine' locally
17-alpine: Pulling from library/postgres
2b0701f72a22: Pull complete
   ... more layers ...
Status: Downloaded newer image for postgres:17-alpine
c268f0b9fbd6dac1c5db40965dec1fcddc675361a69a12de2e510813bdf3f81e
```

Walkthrough of the output:

1. `Unable to find image ... locally` — the image is not on this machine yet. Not an error;
   it just means a download is needed.
2. `Pulling ...` / `Pull complete` — the download, piece by piece. **Pull** is Docker's word
   for downloading an image. Happens only the first time; the image is kept afterwards.
3. `Status: Downloaded newer image` — download finished.
4. The long final line `c268f0b9fbd6...` — the new container's **ID**, a unique fingerprint.
   Seeing an ID means the container was created and started. The name `pg` means you never
   have to type the ID.

The command returned immediately — that is `-d` working. The container keeps running in the
background.

### Important terms

| Term | Meaning |
| --- | --- |
| **Detached (`-d`)** | Run in the background; the terminal comes back at once. |
| **Pull** | Docker's word for downloading an image. |
| **Container ID** | Unique fingerprint of a container, printed by `docker run`. |
| **Superuser** | The `postgres` account that can do anything in the server. |

### Common mistakes

- **Running the same command twice.** Fails with `The container name "/pg" is already in use`.
  A name belongs to one container, even a stopped one. Fix: `docker rm -f pg` or new name.
- **Forgetting `-d`.** The terminal fills with Postgres's startup log and seems stuck. Nothing
  is wrong; the container just was not detached. Ctrl-C stops it.
- **Connecting immediately and getting refused.** The container starting is not the same as
  Postgres inside being ready. There is a gap of a few seconds.

### Where it is used

Developers start throwaway databases this way everywhere: one line, wait a moment, connect.
Later tasks start Redis and other tools with the same pattern — only the image name, port, and
environment variables change.

### Link to the task

Subtask 1 of F5.1 — run the command and name each part out loud. The command has run
successfully on this machine; the container `pg` exists and Postgres is ready. The out-loud
part: say what `-d`, `--name pg`, `-e POSTGRES_PASSWORD=dev`, `-p 5432:5432`, and
`postgres:17-alpine` each do, without looking.

### Summary for revision

- Shape: `docker run [flags] image:tag`. Image last; flags before it.
- `-d` detach, `--name` fixed name, `-e` setting, `-p host:container` door, `image:tag` recipe.
- First run pulls the image; later runs reuse it.
- Output ends with the container ID: created and started.

### Check questions, with answers

**Question 1.** Here is a complete command:
`docker run -d --name cache -e REDIS_PASSWORD=s3cret -p 6380:6379 redis:7-alpine`.
It starts Redis, a different server that conventionally listens on door 6379 inside its
container. Name what each of the five parts after `docker run` does. On which door must a
client on your Mac knock to reach this Redis?

**Answer.** `-d`: run detached, in the background. `--name cache`: call the container `cache`.
`-e REDIS_PASSWORD=s3cret`: hand Redis a setting at start — its password becomes `s3cret`.
`-p 6380:6379`: forward the Mac's door 6380 to the container's door 6379. `redis:7-alpine`:
the image — Redis version 7 on Alpine. A client on the Mac knocks on **6380**, the left
(machine-side) number of the mapping.

**Question 2.** You run
`docker run -d --name pg -e POSTGRES_PASSWORD=dev postgres:17-alpine` — same Postgres command
but with no `-p` flag. The container starts fine and Postgres inside reports it is ready. Can
`pgcli` on your Mac connect to `localhost:5432`?

**Answer.** No. Without a port mapping there is no door through the container's seal. Postgres
is running and healthy, but only reachable from inside the container; a client on the Mac
knocking on `localhost:5432` finds nothing listening and is refused.

### Your doubts

None for this lesson.

---

## Do it yourself — subtasks 1 and 2, from scratch on any laptop

These steps assume a blank machine. They cover everything taught up to Lesson 5.

### Step 0 — Install Docker (one-time setup)

**Mac, option A (simplest):** install [Docker Desktop](https://www.docker.com/products/docker-desktop/),
open the app once, wait for it to say it is running.

**Mac, option B (no app, terminal only — what we used on the main machine):**

```bash
brew install colima docker
colima start
```

`colima start` downloads a small virtual machine the first time; let it finish. After a
reboot, run `colima start` again before using Docker.

**Windows:** install Docker Desktop (it will set up WSL2 if asked). **Linux:** install
`docker` from your distribution and start the service.

**Verify, on any system:**

```bash
docker version
```

Done when you see both a `Client:` and a `Server:` section. If `Server:` shows an error,
Docker's engine is not running — start Docker Desktop (or `colima start`) and retry.

### Subtask 1 — Start the Postgres container

```bash
docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 postgres:17-alpine
```

Expected output, first time (shortened):

```text
Unable to find image 'postgres:17-alpine' locally
17-alpine: Pulling from library/postgres
...
Status: Downloaded newer image for postgres:17-alpine
c268f0b9fbd6dac1c5db40965dec1fcddc675361a69a12de2e510813bdf3f81e
```

The long final line is the new container's ID. Seeing it means: created and started.

Now say each part out loud, without looking:

- `-d` — detached: run in the background, give the terminal back.
- `--name pg` — call the container `pg`.
- `-e POSTGRES_PASSWORD=dev` — environment variable; the image reads it and sets the
  superuser password to `dev`.
- `-p 5432:5432` — port mapping; Mac's door 5432 (left) forwards to container's door 5432
  (right).
- `postgres:17-alpine` — the image: Postgres 17 on Alpine. Always last.

If it fails with `The container name "/pg" is already in use`: a container named `pg` already
exists. Remove it with `docker rm -f pg` and run again.

### Subtask 2 — Check it is alive, read the logs

```bash
docker ps
```

Expected: one row, `NAMES` = `pg`, `STATUS` starting with `Up`, `PORTS` showing
`0.0.0.0:5432->5432/tcp`. No row means not running — run `docker ps -a` to see if it exited.

```bash
docker logs pg
```

Find the readiness line. Careful: on first boot it appears **twice**. The first belongs to a
temporary, internal-only server used during setup. The real one is at the very end, right
after `PostgreSQL init process complete` and `listening on IPv4 address "0.0.0.0", port 5432`:

```text
PostgreSQL init process complete; ready for start up.
... LOG:  listening on IPv4 address "0.0.0.0", port 5432
... LOG:  database system is ready to accept connections
```

Until that last line appears, connecting from outside fails with `connection refused`. That
is the boot gap — the cause of "it worked the second time".

**Done when:** `docker ps` shows `pg` as `Up`, and you can point at the real readiness line
in `docker logs pg` and explain why the earlier identical line does not count.

### For upcoming lessons (optional, install now to save time)

`pgcli` is the client used from Lesson 7 onwards. On a Mac: `brew install pgcli`.

---

## Lesson 5 — Is it alive? `docker ps` and `docker logs`

### Definition

`docker ps` lists the containers running right now. `docker logs <name>` shows everything the
program inside a container has printed since it started. Together they answer: "is it
running?" and "what is it doing?"

### Why it is needed

A detached container is invisible: `docker run -d` prints an ID and goes silent. When a
connection then fails, any of these could be true: the container never started, it started and
crashed, or it is running but Postgres inside is still booting. Without these two commands you
are guessing — re-running things until they work and learning nothing.

### How it works, step by step

1. `docker ps` — one row per running container. If your container is missing, it is not
   running. `docker ps -a` also shows stopped ones, which separates "crashed" from "never
   existed".
2. `docker logs pg` — prints everything the main program in container `pg` has written since
   it started. A recording, not a live view.
3. Postgres writes `database system is ready to accept connections` when it is open for
   business. Until then, clients are refused. `docker run` returns instantly; Postgres needs
   a few seconds — that gap causes the classic early `connection refused`.

### Example — real output from this machine

```text
$ docker ps
CONTAINER ID   IMAGE                COMMAND                  CREATED          STATUS          PORTS                                         NAMES
c268f0b9fbd6   postgres:17-alpine   "docker-entrypoint.s…"   19 minutes ago   Up 19 minutes   0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp   pg
```

```text
$ docker logs pg        (shortened — the interesting lines)
creating configuration files ... ok
syncing data to disk ... ok
20:06:18 LOG:  listening on Unix socket "/var/run/postgresql/.s.PGSQL.5432"
20:06:18 LOG:  database system is ready to accept connections     ← first time: a trap!
20:06:18 LOG:  database system is shut down
PostgreSQL init process complete; ready for start up.
20:06:19 LOG:  listening on IPv4 address "0.0.0.0", port 5432
20:06:19 LOG:  database system is ready to accept connections     ← the real one
```

Walkthrough of `docker ps` columns: `CONTAINER ID` is the short fingerprint; `IMAGE` the
template; `STATUS` is the aliveness answer (`Up` = running); `PORTS` shows the live mapping
(`0.0.0.0:5432->5432/tcp` = the machine's door 5432 forwards to the container's 5432);
`NAMES` the chosen name.

Walkthrough of the log as a story: the first chunk is one-time setup — on its very first
start, Postgres creates its data files, which is why first boot takes seconds. Then a
**temporary** server starts, listening only on a Unix socket (internal-only), says "ready",
and is shut down — it was never reachable from outside. After `PostgreSQL init process
complete`, the real server starts, listens on IPv4 `0.0.0.0` port 5432, and its "ready" line
is the one that counts. Everything before it is the boot gap.

### Important terms

| Term | Meaning |
| --- | --- |
| **`docker ps`** | List running containers. `-a` includes stopped ones. |
| **`docker logs <name>`** | Print everything the container's program has output so far. |
| **Boot gap** | Time between container start and Postgres actually accepting connections. |
| **`Exited (N)`** | STATUS of a stopped container; N is the exit code, 0 = clean, else error. |

### Common mistakes

- **Trusting the first `ready to accept connections` line.** On first boot it appears twice;
  the first belongs to a temporary, internal-only setup server. The real one follows
  `PostgreSQL init process complete` and `listening on IPv4 address "0.0.0.0"`.
- **Treating "container is up" as "service is ready".** `docker ps` says the box is running;
  readiness belongs to the program inside, and only the log shows it.
- **Panicking at an empty `docker ps`.** No row = not running now. `docker ps -a` plus
  `docker logs` tell you whether it crashed and why.

### Where it is used

The universal first move for any misbehaving container, in development and production:
`docker ps` — is it running? `docker logs` — what did it say? The same two commands apply to
your Java app's container later in the course.

### Link to the task

Subtask 2 of F5.1: check aliveness with `docker ps`, read `docker logs pg`, find the
readiness line. Explains the task's "where people get stuck" note: `connection refused` right
after `docker run` means you are in the boot gap — wait, read logs, retry.

### Do it yourself

With the container from subtask 1 running:

```bash
docker ps          # expect: row with NAMES=pg, STATUS starting "Up"
docker logs pg     # find the LAST "database system is ready to accept connections"
```

Done when: you can point at the real readiness line and explain why the earlier identical
line does not count.

### Summary for revision

- `docker ps` = running containers; STATUS `Up` is the aliveness check; `-a` adds stopped.
- `docker logs <name>` = the program's printed history; readiness lives here, not in `ps`.
- First boot prints "ready" twice; only the one after `init process complete` counts.
- Early `connection refused` after `docker run` = boot gap, not a broken tool.

### Check questions, with answers

**Question 1.** `docker ps -a` shows:

```text
CONTAINER ID   IMAGE                COMMAND                  CREATED         STATUS                     PORTS   NAMES
9f1b22ac01de   postgres:17-alpine   "docker-entrypoint.s…"   2 minutes ago   Exited (1) 2 minutes ago           pg
```

(Plain `docker ps` would show nothing here.) Is this container alive? Which single command do
you run next to find out what went wrong?

**Answer.** Not alive. `Exited (1)` means it stopped, and exit code 1 (any non-zero code)
means it stopped because of an error. Run `docker logs pg` — the recording survives the crash
and shows what Postgres printed before dying (for example, a missing required environment
variable).

**Question 2.** Your friend runs
`docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 postgres:17-alpine` for the
first time, gets a container ID, and one second later a connect attempt fails with
`connection refused`. Ten seconds later the same attempt works. They conclude the connect
tool is flaky. What is the real explanation, and which command shows the truth?

**Answer.** The first attempt landed in the boot gap: the container had started, but Postgres
inside was still doing its one-time setup and was not yet listening, so the knock was
refused. Ten seconds later setup had finished and Postgres accepted the connection. Nothing
is flaky. `docker logs pg` shows the truth — the final
`database system is ready to accept connections` line, with timestamps, appears only after
the setup lines.

### Your doubts

None for this lesson.

---

## Lesson 6 — The connection string, five parts

### Definition

A **connection string** is one line of text holding everything a client needs to reach a
database: who is connecting, the password, which machine, which door, and which database.
For Postgres: `postgresql://user:password@host:port/database`.

### Why it is needed

A client needs five values to connect. The connection string packs them into one standard
line every tool understands. If the string is a blob to you, a failed connection cannot be
diagnosed — you cannot tell whether the wrong part is the password, the port, or the database
name. Reading the string lets you name the suspect part in seconds.

### How it works, step by step

```text
postgresql://postgres:dev@localhost:5432/postgres
\_________/  \______/ \_/ \_______/ \__/ \______/
  scheme       user  pass    host   port  database
```

Punctuation is fixed: `:` between user and password, `@` before host, `:` before port, `/`
before database. The five parts:

1. **user** = `postgres` — the account the client presents. Here: the superuser, the built-in
   account that can do anything; it exists in every new Postgres server.
2. **password** = `dev` — must match what the server was given at start
   (`-e POSTGRES_PASSWORD=dev`).
3. **host** = `localhost` — which machine. `localhost` always means "this machine, where the
   client runs". It does not name the container; the port mapping forwards the knock inside.
4. **port** = `5432` — which door on that machine. Must match the **left** number of `-p`.
   With `-p 5433:5432`, the string must say `5433`.
5. **database** = `postgres` — which database inside the server to open. Every new server has
   a built-in database named `postgres`, the landing spot before you create your own.

`postgresql://` is the **scheme** — it announces the kind of string, like `https://` for web
addresses. It is the wrapper, not one of the five parts.

### Example

| Part | Value | Where the value came from |
|------|-------|---------------------------|
| user | `postgres` | Built-in superuser account |
| password | `dev` | Chosen at container start: `-e POSTGRES_PASSWORD=dev` |
| host | `localhost` | The server is reachable on your own machine |
| port | `5432` | The left number of `-p 5432:5432` |
| database | `postgres` | Built-in default database, the landing spot |

Walkthrough — what a client does with the string, in order:

1. Scheme `postgresql://` — speak the Postgres protocol.
2. Knock on host `localhost`, port `5432`. Nothing listening → stop: `connection refused`.
   User, password, database were never checked.
3. Something answered. Present user `postgres`, password `dev`. Wrong password → stop:
   `authentication failed`. This time something answered and refused.
4. Credentials accepted. Open database `postgres`. Missing → `database "..." does not exist`.
5. All five parts worked; the connection is open and SQL can flow.

The order matters: host and port fail first, then credentials, then database. The error tells
you how far you got.

### Important terms

| Term | Meaning |
| --- | --- |
| **Connection string** | One line holding user, password, host, port, database. |
| **Scheme** | The `postgresql://` prefix announcing the string's kind. Not one of the five. |
| **`localhost`** | This machine — the one the client runs on. Never "the container". |

### Common mistakes

- **Reading the two `postgres` words as one thing.** First occurrence = the user (superuser
  account). Last = the database (built-in landing spot). Same word, two meanings.
- **Using the container-side port.** The string's port must match the left (machine-side)
  number of `-p`. With `-p 5433:5432` the string says `5433`.
- **Thinking `localhost` names the container.** It is your own machine; the mapping does the
  forwarding.

### Where it is used

Java uses JDBC URLs (`jdbc:postgresql://...`) — same idea. Deployment tools pass connection
strings as environment variables. Cloud dashboards hand you one as the first thing after
creating a database.

### Link to the task

Subtask 3 of F5.1: break `postgresql://postgres:dev@localhost:5432/postgres` into its five
parts in `NOTES.md`. Checkpoint question 1 asks the five parts of
`postgresql://postgres:dev@localhost:5432/zenith` — same string, database `zenith`.

### Do it yourself

1. Create `NOTES.md` in this task's folder.
2. Write `postgresql://postgres:dev@localhost:5432/postgres` at the top.
3. List the five parts — user, password, host, port, database — one line each: the value,
   what it is, where the value came from. In your own words.
4. Answer out loud: which part changes if the container used `-p 5433:5432`? (Port → 5433.)

**Done when:** `NOTES.md` explains all five parts and you can name the five parts of
`postgresql://postgres:dev@localhost:5432/zenith` without looking.

### Summary for revision

- Shape: `postgresql://user:password@host:port/database`. Scheme is the wrapper.
- Failure order: refused (host/port) → authentication failed (user/password) → does not
  exist (database). The error names the suspect.
- Port = left number of `-p`. `localhost` = this machine, always.
- `postgres` the user and `postgres` the database are different things sharing a name.

### Check questions, with answers

**Question 1.** A teammate sends: `postgresql://app_reader:Winter2026@db.internal.shop.com:6543/orders`.
Name all five parts and their values. Which built-in account is *not* being used here?

**Answer.** user = `app_reader`; password = `Winter2026`; host = `db.internal.shop.com` (a
remote machine, not localhost); port = `6543`; database = `orders`. The built-in superuser
`postgres` is not being used — `app_reader` is a custom, presumably limited account, which is
how production systems do it.

**Question 2.** A container was started with
`docker run -d --name pg -e POSTGRES_PASSWORD=hunter2 -p 5500:5432 postgres:17-alpine`.
Write the connection string a client on the same machine needs to open the built-in
`postgres` database as the superuser. One part is the trap — which?

**Answer.** `postgresql://postgres:hunter2@localhost:5500/postgres`. The trap is the port: the
client must use `5500`, the left (machine-side) number of the mapping. Postgres listening on
5432 inside the container is reachable only through the machine's door 5500.

### Your doubts

None for this lesson.

---

## Lesson 7 — Connecting with `pgcli`, and the five client commands

### Definition

`pgcli` is a terminal client for Postgres: give it a connection string, it opens a connection,
and you type queries at its prompt. The five client commands — `\l`, `\c`, `\dt`, `\d`, `\q` —
are shortcuts for looking around: list databases, switch database, list tables, describe a
table, quit.

### Why it is needed

Java is a terrible place to explore a database from. When you wonder "what tables exist?" or
"did my insert land?", you want to peek directly, in seconds, without writing a program. The
standard client is `psql`, shipped with Postgres; `pgcli` is the same with autocomplete and
colours. Without the five commands you are connected but blind — you cannot name the tables,
so you cannot query them.

### How it works, step by step

1. Run `pgcli <connection string>`. It knocks, authenticates, opens the database, shows a
   prompt.
2. The prompt says where you are: `postgres@localhost:postgres>` reads *user* `@` *host* `:`
   *current database*. Glance at the last part before running anything.
3. Two kinds of lines at the prompt. **SQL** ends with a semicolon: `SELECT now();`.
   **Backslash commands** start with `\`, take no semicolon, and are instructions to the
   client, not SQL — the client turns them into lookups against the server's catalogues.
4. The five commands:

| Command | Does |
| --- | --- |
| `\l` | List all databases in the server. |
| `\c <name>` | Close the current database, open another. The prompt changes. |
| `\dt` | List tables in the current database (its `public` schema). |
| `\d <table>` | Describe one table: every column with its type. |
| `\q` | Quit. |

### Example — real session on this machine

```text
$ pgcli postgresql://postgres:dev@localhost:5432/postgres

postgres@localhost:postgres> \l
+-----------+----------+----------+------------+------------+
| Name      | Owner    | Encoding | Collate    | Ctype      |
|-----------+----------+----------+------------+------------|
| postgres  | postgres | UTF8     | en_US.utf8 | en_US.utf8 |
| template0 | postgres | UTF8     | en_US.utf8 | en_US.utf8 |
| template1 | postgres | UTF8     | en_US.utf8 | en_US.utf8 |
+-----------+----------+----------+------------+------------+

postgres@localhost:postgres> \dt
+--------+------+------+-------+
| Schema | Name | Type | Owner |
|--------+------+------+-------|
+--------+------+------+-------+
SELECT 0

postgres@localhost:postgres> \d event        (a demo table, created then removed)
+-----------+-----------------------------+-----------+
| Column    | Type                        | Modifiers |
|-----------+-----------------------------+-----------|
| id        | integer                     |           |
| name      | text                        |           |
| starts_at | timestamp without time zone |           |
+-----------+-----------------------------+-----------+

postgres@localhost:postgres> \q
Goodbye!
```

Walkthrough:

1. The connect line used the full connection string; the prompt appearing means all five
   parts were right.
2. `\l` shows three databases. `postgres` is the landing spot. `template0` and `template1`
   are Postgres's internal pattern databases — new databases are made by copying a template.
   Leave them alone.
3. `\dt` printed an empty list and `SELECT 0` — zero tables. Not an error; the database has
   no tables yet. An empty list is a true answer.
4. `\d event` shows each column and its type. `timestamp without time zone` is Postgres
   spelling out the `timestamp` type in full.
5. `\q` closes the connection.

### Important terms

| Term | Meaning |
| --- | --- |
| **Prompt** | `user@host:database>` — says who, where, and which database you are in. |
| **Backslash command** | Client shortcut starting with `\`. No semicolon. Not SQL. |
| **`template0`/`template1`** | Internal pattern databases copied to make new ones. Not yours. |

### Common mistakes

- **Semicolon confusion.** `\dt;` may error; `SELECT now()` without `;` makes the client wait
  silently — it thinks the statement is unfinished. Backslash = no semicolon. SQL = semicolon.
- **Panicking at an empty `\dt`.** Often you are just in the wrong database. Read the
  prompt's last part.
- **Typing `\dt` outside `psql`/`pgcli`.** The five commands live in the client, not in SQL.
  In Java or other tools you must use real SQL instead.

### Where it is used

The daily reflex of every backend developer: connect, `\dt`, `\d` the suspicious table, run a
`SELECT`, know in a minute what the app actually stored. Often the first diagnostic step in
production incidents.

### Link to the task

The connect-half of subtask 3 and all of subtask 4 (exactly five commands today). Checkpoint
question 3: `\dt` lists the tables in the current database; `\d event` shows the `event`
table's columns and types.

### Do it yourself

With the `pg` container running:

1. Install the client: `brew install pgcli` (Mac). No-install fallback:
   `docker exec -it pg psql -U postgres` — the standard client inside the container; all five
   commands work the same.
2. Connect: `pgcli postgresql://postgres:dev@localhost:5432/postgres`. Expect the prompt
   `postgres@localhost:postgres>`.
3. `\l` — expect `postgres`, `template0`, `template1`.
4. `\dt` — expect an empty list; this database has no tables, and that is correct.
5. Read the prompt out loud as "user at host, inside database".
6. `\q` — expect `Goodbye!`.

**Done when:** you can connect, say what each of the five commands does without notes, and
explain why `\dt` showed nothing.

### Summary for revision

- `pgcli <connection string>` connects; the prompt is `user@host:database>`.
- SQL needs `;`. Backslash commands do not, and belong to the client.
- `\l` databases, `\c` switch, `\dt` tables, `\d <table>` columns, `\q` quit.
- Empty `\dt` = no tables here = true answer, and maybe the wrong database.

### Check questions, with answers

**Question 1.** You are at this prompt: `postgres@localhost:zenith>`. Without running
anything — who are you connected as, on which machine is the client knocking, and which
database would `\dt` list tables from?

**Answer.** Connected as user `postgres` (the superuser), to host `localhost` (this machine —
forwarded by the port mapping into the container), currently inside database `zenith` — so
`\dt` would list `zenith`'s tables.

**Question 2.** At the prompt, a colleague types `SELECT now()` and presses Enter; the client
prints nothing and seems frozen. Then they type `\dt;` and get a strange error. Explain both.

**Answer.** `SELECT now()` lacks the semicolon, so the client assumes the SQL statement is
unfinished and silently waits for the rest — typing `;` and Enter completes it. `\dt;` is the
reverse mistake: backslash commands are client shortcuts, not SQL, and take no semicolon, so
the `;` confuses the client. SQL = semicolon; backslash = none.

### Your doubts

None for this lesson.

---

## Lesson 8 — Create your own database and prove the connection works

### Definition

`CREATE DATABASE zenith;` makes a new, empty database named `zenith` inside the server.
`SELECT 1;` and `SELECT now();` are the two smallest possible queries, used to prove a
connection truly works before you depend on it.

### Why it is needed

The built-in `postgres` database is a landing spot, not a home — shared, default, and messy if
used for real work. A project gets its own database, with walls around it. And untested
connections cost hours: something fails three steps later and you debug your code when the
real problem is the connection. A thirty-second proof prevents that.

### How it works, step by step

1. `CREATE DATABASE zenith;` is SQL (semicolon!). The server copies its internal pattern
   database `template1` under the new name. Result: a fresh, empty database.
2. Creating does not move you. You stay in `postgres` until `\c zenith` (backslash command,
   no semicolon). The prompt's last part changes to confirm.
3. `SELECT 1;` asks the server to compute the constant 1. It touches no tables, so it works
   in an empty database. If it returns, the whole path works: client, host, port,
   credentials, database, and back.
4. `SELECT now();` same round trip, but returns the server's clock — proving the answer comes
   from the server, not from anything on your side.

### Example — real session on this machine

```text
postgres@localhost:postgres> CREATE DATABASE zenith;
CREATE DATABASE

postgres@localhost:postgres> \c zenith
You are now connected to database "zenith" as user "postgres"

postgres@localhost:zenith> SELECT 1;
+----------+
| ?column? |
|----------|
| 1        |
+----------+

postgres@localhost:zenith> SELECT now();
+-------------------------------+
| now                           |
|-------------------------------|
| 2026-10-23 21:20:12.988343+00 |
+-------------------------------+

postgres@localhost:zenith> \dt
(empty — SELECT 0)
```

`\l` afterwards shows four databases: `postgres`, `template0`, `template1`, `zenith`.

Walkthrough:

1. `CREATE DATABASE` is the server saying "done". The prompt still ends `:postgres` —
   creating did not move you.
2. `\c zenith` reconnects; the prompt now ends `:zenith`. Everything created from here lands
   in `zenith`.
3. `SELECT 1;` returns one cell holding `1`. The header `?column?` means the column was never
   named — harmless.
4. `SELECT now();` ends in `+00`: the server speaks UTC, not your local time. You will meet
   this again and again with databases.
5. `\dt` shows zero tables — `zenith` was copied from an empty template. A blank page.

### Important terms

| Term | Meaning |
| --- | --- |
| **`CREATE DATABASE <name>;`** | SQL that makes a new empty database by copying `template1`. |
| **`SELECT 1;`** | Smallest query; proves the connection without touching tables. |
| **`SELECT now();`** | Returns the server's clock, in UTC. |

### Common mistakes

- **Creating the database and forgetting to switch.** You stay in `postgres`, create tables
  there, and later wonder why `zenith` is empty. Read the prompt.
- **No semicolon on `CREATE DATABASE zenith`.** The client silently waits for the rest of the
  statement. Finish with `;`.
- **Testing a connection with a table query.** In an empty database that fails even when the
  connection is perfect — two possible causes at once. `SELECT 1;` isolates the connection.

### Where it is used

`SELECT 1` is the industry's heartbeat: connection pools fire it to check connections are
alive, and load balancers and monitoring use it as a health check. It appears in real
production configs everywhere.

### Link to the task

Subtask 5 of F5.1, exactly. The `zenith` database is also the home for everything in the rest
of module F5.

### Do it yourself

With the `pg` container running:

1. `pgcli postgresql://postgres:dev@localhost:5432/postgres`
2. `CREATE DATABASE zenith;` → expect `CREATE DATABASE`.
3. `\l` → expect four entries: `postgres`, `template0`, `template1`, `zenith`.
4. `\c zenith` → expect the prompt to end in `:zenith`.
5. `SELECT 1;` → expect one cell containing `1`.
6. `SELECT now();` → expect current UTC time, ending `+00`.
7. `\dt` → expect an empty list.
8. `\q`

**Done when:** `\l` shows `zenith` and both `SELECT`s answer. If step 2 says
`database "zenith" already exists`, you already did it — continue from step 4.

### Summary for revision

- `CREATE DATABASE zenith;` copies `template1` into a fresh database. It does not move you.
- `\c zenith` moves you; the prompt confirms it.
- Prove a connection with `SELECT 1;` (no tables needed), then `SELECT now();` (server's
  clock, UTC).
- Build nothing on an unproven connection.

### Check questions, with answers

**Question 1.** A colleague runs `CREATE DATABASE shop;` at the prompt
`postgres@localhost:postgres>`, sees `CREATE DATABASE`, and immediately creates a table. A day
later they connect to `shop` and find it empty. Where is the table, and which habit would have
caught this?

**Answer.** The table is in the `postgres` database. `CREATE DATABASE` makes the new database
but does not switch into it, and they never ran `\c shop` — so the table landed where they
still were. The habit: read the prompt's last part before creating anything; it still said
`:postgres`.

**Question 2.** You connect to a brand-new, empty database and want to check the connection
end to end. One teammate suggests `SELECT * FROM users;`, another `SELECT 1;`. Which is the
right proof, and why is the other misleading?

**Answer.** `SELECT 1;`. It needs no tables, so the only thing it tests is the connection
path itself. `SELECT * FROM users;` fails in an empty database even when the connection is
perfect — the error would mix "no such table" with "connection broken" and point you at the
wrong problem.

### Your doubts

None for this lesson.

---

## Lesson 9 — Two different failures: "connection refused" vs "authentication failed"

### Definition

**`Connection refused`** means nothing is listening at the door you knocked on.
**`Password authentication failed`** means something answered the door and turned you away.
They look similar in a panic, but they point in opposite directions.

### Why it is needed

A failed connection has two very different suspects: the server side (running? right port?) or
the credentials (right user? right password?). Picking the wrong suspect wastes an afternoon.
The mapping to burn in:

- `connection refused` → nobody is listening → check the container and the port.
- `authentication failed` → server reachable and healthy → check the user and the password.

### How it works, step by step

A client works through the connection string in a fixed order; each error belongs to a step:

1. **Knock on host:port.** No program listening → the operating system rejects the knock:
   `connection refused`. Postgres never saw you. Causes: container not running, wrong port,
   or the boot gap (started but not ready).
2. **Present user and password.** Only happens if the knock succeeded. Wrong password →
   Postgres itself answers: `FATAL: password authentication failed for user "postgres"`.
   `FATAL` plus a sentence about a user = the server speaking = the network path is perfect.

The error tells you how far you got: refused = never arrived; authentication failed = arrived
and bounced at the desk.

### Example — both produced for real on this machine

Wrong password (`dev2`), correct port:

```text
$ pgcli postgresql://postgres:dev2@localhost:5432/postgres
connection to server at "127.0.0.1", port 5432 failed:
FATAL:  password authentication failed for user "postgres"
```

Correct password, wrong port (`5433` — nothing there):

```text
$ pgcli postgresql://postgres:dev@localhost:5433/postgres
connection to server at "127.0.0.1", port 5433 failed: Connection refused
	Is the server running on that host and accepting TCP/IP connections?
```

Walkthrough, wrong password: the knock on 5432 succeeded; the server checked `dev2` and said
no; the message names a *user*, and only Postgres knows about users, so Postgres wrote it.
Container fine, port fine, credentials wrong.

Walkthrough, wrong port: no program listens on 5433; the operating system refused the knock;
the "Is the server running..." line is the client guessing, because it knows nothing beyond
"nobody answered". Postgres never appears in this story.

Wrinkle: `localhost` can point to two addresses (IPv6 `::1` and IPv4 `127.0.0.1`), so the
client may try both and print two differing error lines. If *any* line says `authentication
failed`, the server was reached — credentials are the suspect.

### Important terms

| Term | Meaning |
| --- | --- |
| **`connection refused`** | The operating system's rejection: no program listens on that port. |
| **`FATAL: password authentication failed`** | Postgres's rejection: reached, but credentials wrong. |
| **Triage** | Classifying the error first, so you check the right suspect first. |

### Common mistakes

- **"Fixing" the password when the error said `refused`.** Nobody is listening; passwords are
  irrelevant. Check `docker ps` and the port.
- **Restarting the container when the error said `authentication failed`.** The server is
  healthy; restarting changes nothing. Check user and password.
- **Retrying until it works instead of reading.** The boot gap makes retries sometimes cure
  `refused`, teaching the terrible lesson "run it twice". Read the error; `docker ps` and
  `docker logs pg` give the truth in ten seconds.

### Where it is used

This triage is a networking skill, not a Postgres skill. Web APIs, Redis, your own Java
server — all fail the same two ways: refused = nothing listening; an application-level
rejection = service up, you reached it. Used for the rest of the course and career.

### Link to the task

Subtasks 6 and 7 of F5.1, and checkpoint question 2 verbatim.

### Do it yourself

With the `pg` container running:

1. Wrong password: `pgcli postgresql://postgres:dev2@localhost:5432/postgres`.
   If `pgcli` re-prompts `Password for postgres:`, type `dev2` again to force the real error.
   Expect `FATAL: password authentication failed for user "postgres"`.
   Say out loud: *something answered and refused — credentials problem.*
2. Wrong port: `pgcli postgresql://postgres:dev@localhost:5433/postgres`.
   Expect `Connection refused ... Is the server running on that host ...`.
   Say out loud: *nothing is listening — container, port, or boot gap.*
3. Confirm with `docker ps`: the mapping says `5432`, not `5433` — the refusal makes sense.

**Done when:** you can state instantly which error means "check container and port" and which
means "check user and password".

### Summary for revision

- Refused = never arrived; nobody listening; check `docker ps`, port, boot gap.
- Authentication failed = arrived, bounced; server healthy; check user and password.
- `FATAL` + user name = Postgres itself speaking = network path works.
- Classify first, then act. Never fix the password for a `refused`.

### Check questions, with answers

**Question 1.** A teammate runs `pgcli postgresql://postgres:dev@localhost:5432/postgres` and
gets `connection to server at "127.0.0.1", port 5432 failed: Connection refused`. The password
`dev` is correct. Name two realistic causes, and the two commands that reveal which it is.

**Answer.** Causes: (1) the container is not running — stopped, removed, or crashed; (2) the
container just started and Postgres is still in its boot gap, not yet listening. (A third:
the container maps a different machine-side port than 5432.) The commands: `docker ps` —
is the container `Up`, and what does its PORTS column map? — and `docker logs pg` — did it
crash, and has the final `database system is ready to accept connections` line appeared yet?

**Question 2.** Same teammate, new day, same command, new error:
`FATAL: password authentication failed for user "postgres"`. They say: "the container must
have crashed, I'll restart it." Is that diagnosis possible, given this exact error?

**Answer.** No. That sentence is written by Postgres itself, so Postgres must be running and
reachable — a crashed container would have produced `connection refused` instead. The server
is fine; the user or password in the connection string is wrong.

### Your doubts

None for this lesson.

---

## Lesson 10 — Where the data lives: stop, start, remove, and volumes

### Definition

A container keeps its files while it exists — even stopped — but removing the container
deletes them. A **volume** is a named piece of storage that Docker keeps *outside* the
container and plugs into it, so the data survives even when the container is removed.

### Why it is needed

Without this rule, either you lose real data (`docker rm -f pg` during cleanup, weeks of test
data gone) or you lose trust ("sometimes data survives, sometimes not — Docker is haunted").
It is not haunted. One rule explains everything.

### How it works, step by step

1. **A container has its own private filesystem.** Postgres writes databases inside the
   container, at `/var/lib/postgresql/data`.
2. **Stopping is not removing.** `docker stop pg` ends the program but keeps the container
   and its files; `docker start pg` boots the same container — same files, same databases.
   Like powering a laptop off and on.
3. **Removing deletes the private filesystem.** `docker rm -f pg` (`-f` forces removal even
   while running) throws away the container and everything it wrote. A new `docker run`
   builds a fresh container from the image — and the image never contained your databases.
4. **A volume breaks the link.** `-v pgdata:/var/lib/postgresql/data` says: create (or reuse)
   a named storage area `pgdata`, owned by Docker, mounted where Postgres keeps its data. The
   files now live in `pgdata`, not in the container. Remove the container, start a new one
   with the same `-v`, and the databases are back.

**The rule:** data follows the storage it was written to — the container's private filesystem
dies with the container; a volume outlives it.

### Example — three experiments, run for real on this machine

```text
# Experiment A — stop and start
$ docker stop pg && docker start pg
$ pgcli ...  →  \l
| zenith | ...          ← still there

# Experiment B — remove and recreate (no volume)
$ docker rm -f pg
$ docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 postgres:17-alpine
$ pgcli ...  →  \l
postgres, template0, template1          ← zenith is GONE

# Experiment C — remove and recreate, with a volume
$ docker rm -f pg
$ docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 \
    -v pgdata:/var/lib/postgresql/data postgres:17-alpine
$ pgcli ...  →  CREATE DATABASE zenith;
$ docker rm -f pg
$ docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 \
    -v pgdata:/var/lib/postgresql/data postgres:17-alpine
$ pgcli ...  →  \l
| zenith | ...          ← SURVIVED the removal
```

Walkthrough:

1. **A.** The container was never removed, so its private filesystem — including `zenith` —
   was waiting when Postgres booted again.
2. **B.** Identical command, but the new container got a fresh filesystem copied from the
   image, and the image has never heard of `zenith`. This is the classic data loss.
3. **C.** First run created volume `pgdata`; Postgres wrote everything there. The container
   was removed, but `pgdata` is not part of the container. The next run with the same `-v`
   plugged the same volume back in — `zenith` alive and well.
4. Detail: the second volume-backed boot was fast, with no first-time setup lines — Postgres
   found an already-initialised data directory in the volume and skipped initialisation.

### Important terms

| Term | Meaning |
| --- | --- |
| **`docker stop` / `start`** | Power off / on. Container and its files kept. Reversible. |
| **`docker rm -f`** | Remove the container and its private filesystem. `-f` = even if running. |
| **Volume** | Named storage owned by Docker, outside any container. `-v name:path`. |
| **`/var/lib/postgresql/data`** | The path inside the container where Postgres keeps all data. |

### Common mistakes

- **Confusing `stop` with `rm`.** Stop = power-off, reversible. Rm = shredding, and nothing
  warns you.
- **Thinking the same `docker run` command brings data back.** It recreates the *server*, not
  the *data*.
- **Changing `POSTGRES_PASSWORD` on a volume-backed container.** The variable is read only
  during first-time setup of an empty data directory. With an existing volume, setup is
  skipped — the old password, stored in the volume, still rules.

### Where it is used

Every real deployment puts database storage somewhere that outlives the process: volumes in
development, dedicated disks or managed storage in production. Postgres upgrades work this
way too: new image, same volume. The rest of module F5 relies on the `pgdata` volume.

### Link to the task

Subtask 8 of F5.1 (all three experiments) and checkpoint question 4. Subtask 9 (ask AI to
explain `docker run` step by step and why data disappears without a volume) is answered by
Lessons 4 and 10 together.

### Do it yourself

With the `pg` container from earlier steps running (no volume yet):

1. `docker stop pg`, then `docker start pg`. Connect
   (`pgcli postgresql://postgres:dev@localhost:5432/postgres`), run `\l` — `zenith` still
   there. If refused, that is the boot gap: wait a few seconds, retry.
2. `docker rm -f pg`, recreate **without** volume:
   `docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 postgres:17-alpine`.
   Connect, `\l` — `zenith` gone.
3. `docker rm -f pg`, recreate **with** volume:
   `docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 -v pgdata:/var/lib/postgresql/data postgres:17-alpine`.
   Connect, `CREATE DATABASE zenith;`.
4. Repeat the removal test with the same `-v` command. Connect, `\l` — `zenith` survived.
5. Record all three results in `NOTES.md`: stop/start → kept; remove without volume → lost;
   remove with volume → kept.

**Done when:** `NOTES.md` has the three results and your running container uses the `pgdata`
volume. From now on, always include the `-v` flag for Postgres.

### Summary for revision

- Stop/start: container kept, data kept. Remove: container and its private files gone.
- An identical `docker run` recreates the server, never the data.
- `-v pgdata:/var/lib/postgresql/data` puts the data in Docker-owned storage that outlives
  containers.
- `POSTGRES_PASSWORD` only matters on first initialisation of an empty data directory.

### Check questions, with answers

**Question 1.** A teammate runs Postgres with
`docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 postgres:17-alpine` — no
volume. They `docker stop pg`, go to lunch, `docker start pg`. Data still there? On Friday
they `docker rm -f pg`; on Monday they recreate with the identical command. Data still there?

**Answer.** After stop/start: yes — stopping keeps the container and its private filesystem,
so the data was waiting. After remove/recreate: no — `rm` deleted the private filesystem, and
the identical command only builds a fresh server from the image, which never contained their
data.

**Question 2.** The teammate now always uses `-v pgdata:/var/lib/postgresql/data`. To change
the password they remove the container and recreate it with `-e POSTGRES_PASSWORD=newsecret`
and the same `-v`. Connecting with `newsecret` fails with `authentication failed`; the old
`dev` works. Why?

**Answer.** `POSTGRES_PASSWORD` is read only during first-time setup of an empty data
directory. The `pgdata` volume already holds an initialised data directory — with the old
password stored inside — so the new container skipped setup and ignored the variable. The
server's real password is still `dev`. (Changing it for real is done with SQL, inside the
server.)

### Your doubts

None for this lesson.

---

## Task F5.1 checkpoint — the four "answer out loud" questions, with answers

**1. Name the five parts of `postgresql://postgres:dev@localhost:5432/zenith`.**

user = `postgres` (the superuser account); password = `dev` (set at container start via
`-e POSTGRES_PASSWORD=dev`); host = `localhost` (this machine — the port mapping forwards
into the container); port = `5432` (the machine-side, left number of `-p`); database =
`zenith` (the database to open). `postgresql://` is the scheme — the wrapper, not a part.

**2. What is the difference between "connection refused" and "authentication failed", and
what does each tell you to check?**

`Connection refused` — nothing is listening at that host and port; Postgres never saw you.
Check the server side: is the container running (`docker ps`), is the port right, is it still
in the boot gap (`docker logs pg`). `Authentication failed` — the server answered and
rejected your credentials; the network path is fine. Check the user and password.

**3. What do `\dt` and `\d event` each show you?**

`\dt` lists the tables in the current database (its `public` schema) — name, schema, owner.
`\d event` describes the single table `event`: every column with its type.

**4. You removed the container and lost everything. What would have prevented that?**

A volume: starting the container with `-v pgdata:/var/lib/postgresql/data` puts the data in
Docker-owned storage outside the container, so it survives `docker rm -f`.

**Done-when check:** you can start a container, connect, create a database, and list its
tables without looking up a single command.

---

*Task F5.1 complete. All ten lessons taught, confirmed, and documented. The machine ends with
container `pg` running on the `pgdata` volume, database `zenith` present and empty.*

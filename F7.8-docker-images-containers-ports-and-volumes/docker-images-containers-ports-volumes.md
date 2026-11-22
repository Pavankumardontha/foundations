# F7.8 — Docker: images, containers, ports, and volumes

Notes for Task F7.8. One lesson per section. Each lesson ends with its understanding-check
questions and full answers.

---

## Lesson map

| # | Lesson | Serves |
|---|--------|--------|
| 1 | Image vs container | Checkpoint question 1 |
| 2 | `docker run` and the main command | Why Postgres stays up, `echo` does not |
| 3 | `-e` environment variables | Subtask 7 |
| 4 | `-d` detached mode and `--name` | Subtask 1 |
| 5 | `-p` port mapping | Subtasks 1, 2; checkpoint question 2 |
| 6 | First real run + reading `docker ps` | Subtask 1 |
| 7 | Connecting with `pgcli` | Subtask 2 |
| 8 | `docker logs -f` | Subtask 3 |
| 9 | `docker exec -it` | Subtasks 3, 4; checkpoint question 4 |
| 10 | `docker stop`, `docker rm`, `docker images` | Subtask 3 |
| 11 | Lose data on purpose | Subtask 5; checkpoint question 3 |
| 12 | Named volumes | Subtask 6 |
| 13 | Where volume data lives | Subtasks 6, 10 |
| 14 | Bind mounts | New-words table |
| 15 | The bridge network | Subtask 8 |
| 16 | `docker inspect` | Subtask 8; checkpoint question 5 |
| 17 | `docker stats` and the memory budget | Subtask 9 |
| 18 | Checkpoint | The five questions, done-when |

---

## Lesson 1 — Image vs container: the template and the running copy

### Definition

An **image** is a read-only template. It is a frozen package of files: a program, plus
everything the program needs to run.
A **container** is a running copy made from an image. One image can back many containers at
the same time.

### Why it is needed

Without Docker, you install Postgres directly on your Mac. Pain follows:

- Project A needs Postgres 15. Project B needs Postgres 17. They fight over the same machine.
- Uninstalling leaves files scattered everywhere. The machine slowly rots.
- A teammate installs it slightly differently. Code works on one machine, breaks on the other.

Images and containers remove this pain. The image is sealed and byte-for-byte the same on
every machine. Each container is a separate copy: start one in a second, break it, throw it
away, start a clean one. The Mac stays untouched.

### How it works, step by step

1. An image is a stack of read-only files on disk. For Postgres: the Postgres program, a small
   Linux system around it, and start-up scripts. It just sits there. It never runs and never
   changes.
2. `docker run` creates a container from an image. Docker puts one new, empty, **writable
   layer** of storage on top of the image's read-only files. Then it starts the program inside.
3. The program inside sees both layers as one normal filesystem. It cannot tell them apart.
4. Every file the program writes lands in the writable layer. Never in the image. The image
   stays clean, always.
5. Each container gets its **own** writable layer. Two containers from one image share
   nothing. One writes a file; the other never sees it.
6. Delete the container, and its writable layer is deleted with it. Everything written inside
   is gone.

Point 6 is the heart of this whole task. Tables created inside a Postgres container live in
that writable layer. Delete the container, lose the tables. Volumes exist to fix exactly this
(Lessons 11 and 12).

**Analogy from Java:** an image is like a class, and a container is like an object. One class,
many objects. Each object has its own fields. Changing a field on one object changes nothing
in the class, and nothing in other objects.

### Example — real commands, real output from this machine

`alpine` is a tiny Linux image, about 13 MB. Perfect for throwaway experiments.

**Step 1 — download a template.**

```bash
docker pull alpine
```

```text
Using default tag: latest
latest: Pulling from library/alpine
Status: Downloaded newer image for alpine:latest
```

Nothing is running yet. Only a template was fetched.

**Step 2 — list the templates on this machine.**

```bash
docker images
```

```text
IMAGE                ID             DISK USAGE   CONTENT SIZE   EXTRA
alpine:latest        f59abe656b69       13.6MB         4.28MB
postgres:17-alpine   eee6c6e1b089        415MB          116MB   U
```

Two images: alpine, and the Postgres image from Task F5.1. Both are just files on disk.

**Steps 3 and 4 — make two copies from the one alpine template.**

```bash
docker run --name box1 alpine echo "hello from box1"
docker run --name box2 alpine echo "hello from box2"
```

```text
hello from box1
hello from box2
```

Each `docker run` created a brand-new container, ran `echo` inside it, and printed the text.
`echo` finishes instantly. When the command inside finishes, the container stops.

**Step 5 — list all containers.**

```bash
docker ps -a
```

```text
CONTAINER ID   IMAGE                COMMAND                  CREATED         STATUS                     PORTS                    NAMES
182155b36a7a   alpine               "echo 'hello from bo…"   4 seconds ago   Exited (0) 3 seconds ago                            box2
f271be49abcd   alpine               "echo 'hello from bo…"   8 seconds ago   Exited (0) 7 seconds ago                            box1
e931f86c4496   postgres:17-alpine   "docker-entrypoint.s…"   3 weeks ago     Up 5 days                  0.0.0.0:5432->5432/tcp   pg
```

The `IMAGE` column: `box1` and `box2` both came from `alpine`. One template, two separate
copies. `Exited (0)` means: stopped, and the command inside ended without error. The `pg` row
is the Postgres container from Task F5.1, still running.

**Step 6 — proof that a stopped container still exists.** Run the `box1` command again:

```bash
docker run --name box1 alpine echo "hello again"
```

```text
docker: Error response from daemon: Conflict. The container name "/box1" is already in use by
container "f271be49abcd...". You have to remove (or rename) that container to be able to
reuse that name.
```

| Error text | Plain meaning |
|---|---|
| `Conflict. The container name "/box1" is already in use` | A container called `box1` still exists. Finishing did not delete it. |
| `You have to remove (or rename) that container` | Names must be unique. Delete the old `box1` first, or pick a new name. |

**A stopped container is still a container.** It keeps its name and its writable layer until
you delete it.

**Step 7 — delete the copies. The template is untouched.**

```bash
docker rm box1 box2
docker images
```

```text
box1
box2
IMAGE                ID             DISK USAGE   CONTENT SIZE   EXTRA
alpine:latest        f59abe656b69       13.6MB         4.28MB
postgres:17-alpine   eee6c6e1b089        415MB          116MB   U
```

`docker rm` deletes containers. Both copies are gone. Both images are still there. Deleting
copies never touches the template.

### Important terms

| Term | Meaning |
|---|---|
| Image | Read-only template: a frozen package of a program and all it needs. |
| Container | A running copy made from an image. |
| Writable layer | The one per-container storage layer where all its writes land. Deleted with the container. |
| `docker pull <image>` | Download an image to this machine. |
| `docker run --name <n> <image> <cmd>` | Create a new container named `<n>` from `<image>`, run `<cmd>` inside. |
| `docker ps -a` | List all containers. Without `-a`: only running ones. |
| `docker rm <n>` | Delete a stopped container. |
| `docker images` | List all images on this machine. |
| `Exited (0)` | Container stopped; the command inside ended without error. |

### Common mistakes

- **Thinking `docker rm` of a container deletes the image.** It does not. The template stays.
- **Thinking two containers from one image share changes.** They do not. Each has its own
  writable layer.
- **Thinking a finished container is gone.** It is not. It sits in `Exited` state, holding its
  name. That is what the `Conflict` error above was saying.
- **Saying "I changed the image" after writing a file inside a container.** Impossible. Writes
  only land in that container's writable layer. The image is read-only.

### Where it is used

- This project: Postgres, Redis, and Kafka each run as a container. Nothing gets installed on
  the Mac itself.
- Real servers: almost every company ships its apps as images and runs them as containers.
- CI pipelines: tests run inside fresh containers, so every run starts clean.

### Link to the task

- Checkpoint question 1, word for word: "What is the difference between an image and a
  container?"
- Subtask 1 needs it: `docker run` creates a fresh container from the `postgres` image.
- Subtask 5, the lose-data experiment, is step 6 of "How it works" happening to real tables:
  delete the container, and its writable layer dies with it.

### Do it yourself

Setup check:

```bash
docker --version
```

Expect something like `Docker version 29.x.x`. If `command not found`: install Docker Desktop
with `brew install --cask docker`, open the Docker app once, wait for the whale icon in the
menu bar to say it is running, then check again.

Steps, in order:

```bash
docker pull alpine                                      # 1. get the template
docker images                                           # 2. expect: alpine:latest listed
docker run --name box1 alpine echo "hello from box1"    # 3. expect: hello from box1
docker run --name box2 alpine echo "hello from box2"    # 4. expect: hello from box2
docker ps -a                                            # 5. expect: box1 and box2, both Exited (0)
docker run --name box1 alpine echo "hello again"        # 6. expect: FAILS with "Conflict. The container name /box1 is already in use"
docker rm box1 box2                                     # 7. prints: box1, box2
docker images                                           # 8. expect: alpine:latest STILL listed
```

**Done when:** step 6 fails with the Conflict error, step 8 still shows `alpine`, and you can
say in one sentence why step 6 failed.

### Summary for revision

- Image = read-only template. Container = running copy. One image, many containers.
- `docker run` = new container = image + one fresh writable layer.
- All writes land in the container's own writable layer. Containers share nothing.
- A finished container still exists (`Exited`), holding its name, until `docker rm`.
- Deleting containers never touches images.
- Delete a container and its writable layer — with everything written inside — is gone.

### Check questions, with answers

**Question 1.** A machine shows this:

```text
$ docker images
IMAGE           ID             DISK USAGE
alpine:latest   f59abe656b69       13.6MB

$ docker ps -a
CONTAINER ID   IMAGE    COMMAND        STATUS                      NAMES
a1a1a1a1a1a1   alpine   "echo one"     Exited (0) 9 seconds ago    run-one
b2b2b2b2b2b2   alpine   "echo two"     Exited (0) 19 seconds ago   run-two
c3c3c3c3c3c3   alpine   "echo three"   Exited (0) 29 seconds ago   run-three
```

(a) How many images are on this machine, and how many containers?
(b) Now `docker rm run-one run-two run-three` deletes all three containers. Is the `alpine`
image still on the machine? Why?

**Answer.**
(a) One image (`alpine:latest`). Three containers (`run-one`, `run-two`, `run-three`), all
created from that one image.
(b) Yes, the image is still there. Images and containers are separate things. `docker rm`
only deletes containers — the copies. The template is untouched, however many copies you
delete.

**Question 2.** A container is started from the `alpine` image. A program inside it writes a
file at `/notes/today.txt`. Then the container is deleted with `docker rm`. A new container
is started from the same `alpine` image. Does `/notes/today.txt` exist in the new container?
Say where the file was stored, and what happened to it.

**Answer.** No, the file does not exist in the new container. The file was stored in the first
container's writable layer — never in the image, because the image is read-only. `docker rm`
deleted that container, and its writable layer with it. The new container starts from the
clean image plus a fresh, empty writable layer. So `/notes/today.txt` is gone.

### Your doubts

None in this lesson.

---

## Lesson 2 — `docker run` and the main command

### Definition

`docker run` creates a brand-new container from an image, then starts it.
Every container runs exactly one **main command**: the program it was born to run.
The rule: **a container lives exactly as long as its main command lives.**

### Why it is needed

Without this rule, Docker looks random. An `echo` container stops in under a second. A
Postgres container runs for weeks. Same tool, opposite behavior. One rule explains both.
Detached mode, `stop`, and `logs` (later lessons) only make sense through it.

### How it works, step by step

1. Every image carries a **default main command**, baked in when the image was built. For
   `alpine` it is `/bin/sh` — a command prompt program. For `postgres:17-alpine` it is a
   start-up script that launches the Postgres server.
2. `docker run <image>` starts the default. Words written after the image name **replace**
   the default. `docker run alpine echo "hi"` makes `echo` the main command.
3. While the main command runs, `docker ps` shows the container as `Up`.
4. When the main command ends, the container stops. Status becomes `Exited (code)`. The code
   is the main command's exit code. `0` means it ended without error.
5. The `COMMAND` column in `docker ps` shows each container's main command.
6. Server programs are loops that never end on their own. Their containers stay `Up` until
   something stops them.

### Example — real commands, real output from this machine

**Step 1 — a container with a 15-second life.** `sleep` is a program that waits the given
number of seconds, then exits with code 0.

```bash
docker run --name slow alpine sleep 15
```

The terminal is held for 15 seconds. The main command runs in the foreground and owns the
terminal until it ends. This pain is what `-d` (Lesson 4) removes.

**Step 2 — in a second terminal, while `slow` is alive:**

```text
$ docker ps
CONTAINER ID   IMAGE     COMMAND      CREATED         STATUS         PORTS     NAMES
21492c14b220   alpine    "sleep 15"   3 seconds ago   Up 3 seconds             slow
```

`COMMAND` shows the main command. `STATUS Up 3 seconds` means it is still running.

**Step 3 — after the 15 seconds ended:**

```text
$ docker ps -a
CONTAINER ID   IMAGE                COMMAND                  CREATED          STATUS                      PORTS     NAMES
21492c14b220   alpine               "sleep 15"               29 seconds ago   Exited (0) 13 seconds ago             slow
e931f86c4496   postgres:17-alpine   "docker-entrypoint.s…"   3 weeks ago      Exited (0) 14 hours ago               pg
```

`slow` exited when its main command finished. The second row was a real surprise: the `pg`
container from Task F5.1 had said `Up 5 days` the evening before. Overnight, Docker itself
shut down on this Mac. When Docker shuts down, it stops every container's main command. Main
command ends, container exits. A container is not a machine that stays on. It is one program.

**Step 4 — the instant death.** Run alpine with no command after the image name:

```bash
docker run --name ghost alpine
```

It returns immediately:

```text
$ docker ps -a
CONTAINER ID   IMAGE     COMMAND      CREATED                  STATUS                              NAMES
a0a097618eef   alpine    "/bin/sh"    Less than a second ago   Exited (0) Less than a second ago   ghost
```

| What you see | Plain meaning |
|---|---|
| `COMMAND` is `"/bin/sh"` | No command was given, so the image's default main command ran: a shell. |
| `Exited (0) Less than a second ago` | A shell with nobody typing into it has nothing to do. It ends at once. Main command ends, container dies. |

This is the famous "I ran a container and it vanished" mystery. It did not vanish. Its main
command ended.

**Step 5 — cleanup, and one repair.**

```bash
docker rm slow ghost
docker start pg
```

`docker start` brings an existing, stopped container back to life (full treatment in
Lesson 10). Note what did NOT happen: `pg` stopped 14 hours ago but was never deleted. Its
writable layer survived the stop.

### Important terms

| Term | Meaning |
|---|---|
| Main command | The one program a container runs. Container life = main command life. |
| Default main command | The command baked into the image, used when you give none. |
| `Up X seconds` | The main command is still running. |
| `Exited (code)` | The main command ended with that exit code. `0` = no error. |
| `sleep N` | A program that waits N seconds, then exits with code 0. |
| `/bin/sh` | A shell: a command prompt program. Alpine's default main command. |
| `docker start <n>` | Restart an existing, stopped container. Not the same as `docker run`. |

### Common mistakes

- **"My container vanished."** No. Its main command ended. It is in `docker ps -a` as `Exited`.
- **Expecting `docker run alpine` to drop you into a prompt inside the container.** It will
  not. The shell ends instantly with nobody attached. The proper way in is `exec -it`
  (Lesson 9).
- **Thinking a container is a little virtual machine that stays on.** It is a wrapper around
  one program. No program running, no container running.
- **Running `docker run` again to "restart" a stopped container.** That creates a brand-new
  container. With the same `--name` it fails with the `Conflict` error. Restarting an
  existing container is `docker start`.

### Where it is used

- Reading `docker ps` anywhere: `COMMAND` and `STATUS` have exact meanings.
- Debugging: "why did my container exit?" always means "why did its main command end?" The
  exit code is the first clue.
- Phase 13: writing a Dockerfile is largely choosing an image's default main command.

### Link to the task

Subtask 1 says: run Postgres **detached**. The Postgres main command never ends on its own,
so without detaching it would hold the terminal forever. Subtask 1 also says: read every
`docker ps` column — `COMMAND` and `STATUS` are now understood.

### Do it yourself

You need two terminal windows.

```bash
# Window 1:
docker run --name slow alpine sleep 15     # holds the window for ~15 seconds

# Window 2, while window 1 is held:
docker ps                                  # expect: slow, COMMAND "sleep 15", STATUS Up X seconds

# Window 1, after it returns:
docker ps -a                               # expect: slow  Exited (0)
docker run --name ghost alpine             # returns immediately
docker ps -a                               # expect: ghost  "/bin/sh"  Exited (0) less than a second ago
docker rm slow ghost                       # cleanup; prints: slow, ghost
```

**Done when:** you saw `slow` as `Up` in window 2 while window 1 was held, you saw `ghost`
exit instantly, and you can say in one sentence why `ghost` died at once but `slow` lived 15
seconds.

### Summary for revision

- `docker run` = create a new container and start its main command.
- Container life = main command life. `Up` while it runs, `Exited (code)` when it ends.
- No command after the image name → the image's default main command runs.
- Words after the image name replace the default main command.
- Servers never end on their own; their containers stay `Up` until stopped.
- Docker shutting down stops every main command. Stopped is not deleted: `docker start`
  revives a stopped container.

### Check questions, with answers

**Question 1.** You run this command. `sleep` is a program that waits the given number of
seconds, then exits with code 0:

```bash
docker run --name waiter alpine sleep 60
```

(a) Ten seconds later, in another terminal, you run `docker ps`. Is `waiter` in the list?
(b) Two minutes later you run `docker ps` again. Is `waiter` in the list?
(c) Same moment, you run `docker ps -a`. What does the `STATUS` column show for `waiter`?

**Answer.**
(a) Yes. The main command `sleep 60` is still running, so `waiter` shows as `Up 10 seconds`.
(b) No. After 60 seconds the main command exited. Plain `docker ps` lists only running
containers.
(c) `Exited (0) About a minute ago`. The main command ended with code 0. The container
stopped, but it still exists.

**Question 2.** On some machine, `docker ps -a` shows these two containers:

```text
IMAGE                COMMAND                  STATUS         NAMES
postgres:17-alpine   "docker-entrypoint.s…"   Up 3 weeks     db
alpine               "echo 'hi'"              Exited (0)     greeter
```

Both were started with `docker run`. Why has `db` been running for three weeks, while
`greeter` stopped in under a second?

**Answer.** A container lives as long as its main command. `db`'s main command is the
Postgres server — a loop that runs until told to stop — so the container stays `Up`.
`greeter`'s main command was `echo 'hi'`, which prints its text and ends instantly, so the
container exited at once.

### Your doubts

None in this lesson.

---

## Lesson 3 — `-e`: environment variables, how a container gets its settings

### Definition

An **environment variable** is a named value handed to a program when it starts. Example:
`POSTGRES_PASSWORD=dev`. The `-e NAME=value` flag on `docker run` places that variable inside
the new container, where its main command can read it.

### Why it is needed

An image is read-only and byte-for-byte identical on every machine. But a container needs
*your* settings: your password, your database name. The template cannot be edited. So there
must be a door to pass settings through at start time. `-e` is that door. The pain is real:
the Postgres image refuses to start if it gets no password.

### How it works, step by step

1. Every running program receives a set of named values at birth. These are its environment
   variables. The Mac shell has them too — `HOME`, `PATH`.
2. `-e NAME=value` tells Docker: when starting this container's main command, include this
   variable in its set.
3. What happens next is the image's choice. The Postgres image's start-up script looks for
   specific names it documents: `POSTGRES_PASSWORD` (required), `POSTGRES_USER` and
   `POSTGRES_DB` (optional).
4. The script reads them on **first start** — when its data folder is still empty. It creates
   the database and sets the superuser password from `POSTGRES_PASSWORD`. On later starts
   over existing data, it does not apply them again. This matters in Lesson 12.
5. Position matters. Flags like `-e` go **before** the image name. Words **after** the image
   name replace the main command.

### Example — real commands, real output from this machine

**Step 1 — the mechanism, pure.** `env` is a program that prints every environment variable
it received.

```bash
docker run --name peek -e GREETING=hello alpine env
```

```text
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
HOSTNAME=5140ec325504
GREETING=hello
HOME=/root
```

`PATH`, `HOSTNAME`, `HOME`: every container gets these automatically. `GREETING=hello`: ours,
delivered by `-e`.

**Step 2 — Postgres with no password. The refusal.**

```bash
docker run --name nopass postgres:17-alpine
```

```text
Error: Database is uninitialized and superuser password is not specified.
       You must specify POSTGRES_PASSWORD to a non-empty value for the
       superuser. For example, "-e POSTGRES_PASSWORD=password" on "docker run".
```

```text
$ docker ps -a
CONTAINER ID   IMAGE                COMMAND                  CREATED         STATUS                     NAMES
639b903b20c8   postgres:17-alpine   "docker-entrypoint.s…"   5 seconds ago   Exited (1) 4 seconds ago   nopass
```

| What you see | Plain meaning |
|---|---|
| `Database is uninitialized` | First start: the data folder is empty. The script must create a database now. |
| `superuser password is not specified` | The script looked for `POSTGRES_PASSWORD` among its environment variables. It was not there. |
| `Exited (1)` | The main command ended with exit code 1. Not 0. 1 means: ended with an error. |

**Step 3 — same image, one flag added. The fix.**

```bash
docker run --name haspass -e POSTGRES_PASSWORD=dev postgres:17-alpine
```

```text
The files belonging to this database system will be owned by user "postgres".
...
PostgreSQL init process complete; ready for start up.
2026-11-18 07:26:07 UTC [1] LOG:  database system is ready to accept connections
```

From a second terminal:

```text
$ docker ps
CONTAINER ID   IMAGE                COMMAND                  CREATED          STATUS         PORTS      NAMES
84558f0c1004   postgres:17-alpine   "docker-entrypoint.s…"   10 seconds ago   Up 9 seconds   5432/tcp   haspass
```

Same image, one `-e` flag different, opposite outcome. The boot log is the "first start"
work happening live: the script found the password and built a fresh database with it.
The run held the terminal (the server never ends); cleanup was `docker stop haspass` and
`docker rm haspass` from the second terminal. Both commands get full treatment in Lesson 10.

### Important terms

| Term | Meaning |
|---|---|
| Environment variable | A named value a program receives at start. |
| `-e NAME=value` | Docker flag: put this variable into the container. Goes before the image name. |
| `env` | A program that prints every environment variable it received. |
| First start | The first run over an empty data folder. Only then does Postgres apply `POSTGRES_PASSWORD`. |
| `Exited (1)` | Main command ended with an error. `0` means ended fine. |
| `POSTGRES_PASSWORD` | Required by the Postgres image on first start. `POSTGRES_USER`, `POSTGRES_DB` are optional. |

### Common mistakes

- **Putting `-e` after the image name.** Everything after the image name replaces the main
  command. Docker flags go before the image name.
- **A typo in the variable name.** `-e POSTGRES_PASWORD=dev` — Docker sets the misspelled
  variable happily. The start-up script ignores unknown names. The refusal still appears.
  The names are a contract defined by the image; spelling is everything.
- **Thinking `-e` changes the image.** It fills one container's set of variables. The image
  never changes.
- **Expecting a new password to apply to an old database.** `POSTGRES_PASSWORD` is applied on
  first start only, when the data folder is empty. Lesson 12 shows this bite for real.

### Where it is used

- The Postgres image: `POSTGRES_PASSWORD`, `POSTGRES_USER`, `POSTGRES_DB`. Redis and Kafka
  images follow the same pattern with their own names.
- Spring Boot (Task F6.4): environment variables rank near the top of the configuration
  precedence order. Inside containers, configuration arrives as environment variables almost
  every time. One mechanism, used by Postgres and by your own app.
- CI pipelines and cloud platforms inject secrets and settings the same way.

### Link to the task

- This lesson is subtask 7, word for word: `-e POSTGRES_PASSWORD=dev` sets an environment
  variable inside the container, and the Postgres image reads it on first start.
- Subtask 1 needs it: the real Postgres run carries `-e POSTGRES_PASSWORD=dev`.
- The task's note is now clear: this is the same mechanism the Spring Boot app uses, which is
  why container configuration is environment variables almost every time.

### Do it yourself

```bash
docker run --name peek -e GREETING=hello alpine env    # expect: GREETING=hello in a short list
docker rm peek

docker pull postgres:17-alpine                         # first time only: downloads ~100 MB

docker run --name nopass postgres:17-alpine            # expect: FAILS, "must specify POSTGRES_PASSWORD"
docker ps -a                                           # expect: nopass  Exited (1)
docker rm nopass

docker run --name haspass -e POSTGRES_PASSWORD=dev postgres:17-alpine
# expect: boot lines, ending with: database system is ready to accept connections
# the window is now held — the server is the main command. Press Ctrl-C to stop it.
# expect after Ctrl-C: shutdown lines, then the prompt returns.
docker rm haspass
```

**Done when:** `nopass` failed with the password error and shows `Exited (1)`; `haspass`
reached "ready to accept connections"; and you can say in one sentence what `-e` does.

### Summary for revision

- `-e NAME=value` puts one named value into the container's environment variables.
- Every container also gets `PATH`, `HOSTNAME`, `HOME` automatically.
- The image decides which names mean something. Postgres requires `POSTGRES_PASSWORD`.
- Postgres applies these values on first start only, over an empty data folder.
- Flags before the image name. Words after the image name replace the main command.
- Exit code 1 = ended with an error. Exit code 0 = ended fine.

### Check questions, with answers

**Question 1.** Here is a command:

```bash
docker run --name db -e POSTGRES_PASSWORD=s3cret postgres:17-alpine
```

(a) In plain words: what does the `-e POSTGRES_PASSWORD=s3cret` part do?
(b) Which program reads that value, and at what moment?

**Answer.**
(a) It places the environment variable `POSTGRES_PASSWORD`, with value `s3cret`, inside the
new container — into the set of named values its main command receives at start.
(b) The Postgres image's start-up script reads it, at first start, when the data folder is
still empty. It creates the database and sets the superuser password to `s3cret`.

**Question 2.** A friend runs this and shows you the result:

```text
$ docker run --name db postgres:17-alpine
Error: Database is uninitialized and superuser password is not specified.
       You must specify POSTGRES_PASSWORD to a non-empty value for the
       superuser. For example, "-e POSTGRES_PASSWORD=password" on "docker run".
```

(a) Explain to the friend, in one or two sentences, what went wrong.
(b) Write the corrected command.

**Answer.**
(a) The Postgres start-up script needs a password to create its first database. It looked for
the environment variable `POSTGRES_PASSWORD`, found nothing, printed instructions, and quit
with exit code 1.
(b) First remove the failed container, because it still holds the name `db`. Then run again
with the flag:

```bash
docker rm db
docker run --name db -e POSTGRES_PASSWORD=s3cret postgres:17-alpine
```

Without the `docker rm`, the new run fails with the `Conflict` name error — a stopped
container keeps its name until deleted.

### Your doubts

None in this lesson.

---

## Lesson 4 — `-d` detached mode, and `--name`

### Definition

`-d` means **detached mode**: the container starts in the background. `docker run` prints the
new container's ID and gives the terminal back at once.
`--name` gives the container a name you choose. The name is the handle used in every later
command.

### Why it is needed

The Postgres server is a main command that never ends on its own. Run it plain, and it holds
the terminal hostage forever. One terminal window per server does not scale. And without
names, every later command needs a container ID like `05c945e41784`. `-d` frees the terminal.
`--name` gives a word instead of an ID.

### How it works, step by step

1. `docker run -d` creates the container exactly as always: image plus a fresh writable layer.
2. It starts the main command, but does not tie it to the terminal.
3. It prints the new container's full 64-character ID. The prompt returns.
4. The container keeps running in the background. `docker ps` shows it.
5. `--name` attaches a chosen name. Names must be unique — a stopped container keeps its name
   until deleted.
6. With no `--name`, Docker invents a name: a random adjective plus a random surname.
7. Later commands accept the name or the ID: `docker stop pg`, `docker rm camper`.

What `-d` does **not** do: it does not change the life rule. The container still lives exactly
as long as its main command. `-d` only changes who owns the terminal.

### Example — real commands, real output from this machine

**Step 1 — two detached runs. One named, one not.**

```text
$ docker run -d --name camper alpine sleep 300
05c945e41784ac28ac81c3da9dc94b753bd5c9afce5f23bf03e6441e0ad70875

$ docker run -d alpine sleep 300
ffad8be0afec38f82685487a42b675caa4b3b6823a70a0b57e94f5310a0e613e
```

Each printed an ID and returned instantly. No held terminal.

**Step 2 — the names.**

```text
$ docker ps
CONTAINER ID   IMAGE                COMMAND                  CREATED         STATUS         PORTS                    NAMES
ffad8be0afec   alpine               "sleep 300"              4 seconds ago   Up 3 seconds                            romantic_panini
05c945e41784   alpine               "sleep 300"              4 seconds ago   Up 3 seconds                            camper
e931f86c4496   postgres:17-alpine   "docker-entrypoint.s…"   3 weeks ago     Up 6 minutes   0.0.0.0:5432->5432/tcp   pg
```

`camper` — chosen. `romantic_panini` — Docker's invention for the unnamed one. The
`CONTAINER ID` column is the first 12 characters of the long ID printed at run time
(`ffad8be0afec...`). `pg` has been a detached, named container all along, since Task F5.1.

**Step 3 — the trap: `-d` with a command that ends instantly.**

```text
$ docker run -d --name quick alpine echo "bye"
1118201f881c75df0d5c7f4ee821ed27084ee9abd35137f85fc22a30963d6e75

$ docker ps -a --filter name=quick     (seven seconds later)
CONTAINER ID   IMAGE     COMMAND      CREATED         STATUS                     NAMES
1118201f881c   alpine    "echo bye"   7 seconds ago   Exited (0) 6 seconds ago   quick
```

| What happened | Plain meaning |
|---|---|
| An ID was printed | `-d` worked. The container started in the background. |
| `Exited (0)` seconds later | `echo` ended. Main command ends, container exits. `-d` is not life support. |
| "bye" never appeared on screen | Detached output goes to the container's log, not the terminal. `docker logs` reads it (Lesson 8). |

**Step 4 — cleanup.**

```bash
docker rm -f camper quick romantic_panini
```

`rm -f` means force: stop the container first if it is running, then delete it, in one step.

### Important terms

| Term | Meaning |
|---|---|
| Detached mode (`-d`) | Start the container in the background. Terminal returns at once. |
| `--name <word>` | Chosen name for the container. The handle for all later commands. |
| Invented name | Docker's random `adjective_surname` when no `--name` is given. |
| Container ID | 64 characters, printed by `docker run -d`. `docker ps` shows the first 12. |
| `docker rm -f <n>` | Force-remove: stop if running, then delete, one step. |

### Common mistakes

- **Thinking `-d` changes the container's behavior.** It only frees the terminal. A detached
  container dies the same way: when its main command ends.
- **Forgetting `--name` on long-lived containers.** The result is typing IDs or
  `romantic_panini` forever.
- **Expecting the command's output on screen with `-d`.** Only the ID appears. The output
  lives in the container's log, read by `docker logs` (Lesson 8).
- **Flags after the image name.** `docker run alpine -d sleep 300` does not detach. Words
  after the image name become the main command, so Docker tries to run a program called `-d`,
  which does not exist. Flags before the image name, always.

### Where it is used

- Every server container runs detached with a name: Postgres, Redis, Kafka, and later the
  app itself.
- Foreground runs (no `-d`) are for one-shot tools and debugging, like the `env` experiment.

### Link to the task

Subtask 1: "Run Postgres detached with a name, a password, and a mapped port." Three of the
four pieces are now in hand: `-d`, `--name`, `-e POSTGRES_PASSWORD=dev`. The mapped port
(`-p`) is Lesson 5.

### Do it yourself

```bash
docker run -d --name camper alpine sleep 300   # expect: 64-char ID, prompt back instantly
docker ps                                      # expect: camper, STATUS Up X seconds
docker run -d alpine sleep 300                 # expect: another long ID
docker ps                                      # expect: a container with an invented name
docker run -d --name quick alpine echo "bye"   # expect: ID printed, nothing else
docker ps -a                                   # expect: quick  Exited (0) — -d is not life support
docker rm -f camper quick                      # also add the invented name you saw
```

**Done when:** the prompt came back instantly after each `-d` run, you spotted Docker's
invented name, and you can explain in one sentence why `quick` shows `Exited` even though
`-d` was used.

### Summary for revision

- `-d` = background start. `docker run` prints the 64-character ID and returns.
- `-d` does not change the life rule: main command ends, container exits.
- Detached output goes to the container's log, not the screen.
- `--name` = the handle for every later command. No name → invented `adjective_surname`.
- `docker ps` IDs are the first 12 characters of the long ID.
- `docker rm -f` = stop if needed, then delete.

### Check questions, with answers

**Question 1.** You run this. `sleep` is a program that waits the given number of seconds,
then exits with code 0:

```bash
docker run -d --name waiter alpine sleep 600
```

(a) What appears in the terminal right after you press enter?
(b) Is the terminal free to use?
(c) Eleven minutes later: what does plain `docker ps` show for `waiter`, and what does
`docker ps -a` show?

**Answer.**
(a) The new container's 64-character ID, and nothing else.
(b) Yes. Detached mode returns the prompt at once; the container runs in the background.
(c) Plain `docker ps`: `waiter` is absent — `sleep 600` ended after ten minutes, and plain
`ps` lists only running containers. `docker ps -a`: `waiter` with STATUS
`Exited (0) About a minute ago`.

**Question 2.** A teammate runs this:

```bash
docker run -d --name greet alpine echo "hello"
```

An ID is printed. One second later the teammate runs `docker ps` and `greet` is not in the
list. The teammate says: "`-d` is broken." In one or two sentences, give the real explanation.
Then name the command that proves `greet` still exists.

**Answer.** `-d` is fine. The main command `echo` printed into the container's log and ended
instantly, so the container exited — detached mode does not keep a finished command alive.
Plain `docker ps` lists only running containers. The proof is `docker ps -a`, which shows
`greet` with `Exited (0)`.

### Your doubts

None in this lesson.

---

## Lesson 5 — `-p`: port mapping, host first, container second

### Definition

A **port** is a numbered door on a machine. Programs that serve others listen behind a door
number. `-p HOST:CONTAINER` connects one door on your Mac to one door inside the container.
Left number: your Mac. Right number: inside the container. Always.

### Why it is needed

A container lives behind its own wall, with its own private set of doors (the bridge network,
Lesson 15). Postgres inside a container listens on the container's door 5432. But `pgcli` on
the Mac can only knock on the Mac's own doors — `localhost`. Without `-p`, the two sets of
doors are not connected. The server can be perfectly healthy and still unreachable.

### How it works, step by step

1. Read `-p 8080:80` as: "my Mac's door 8080 forwards to the container's door 80."
2. Memory trick: you stand on your own machine, so your side comes first. Left is where you
   knock. Right is where the program listens.
3. Docker opens door HOST on the Mac. Every knock on it is forwarded to door CONTAINER inside.
4. The container side is fixed by the program. Postgres always listens on 5432 inside its
   container. Nginx on 80. Only the host side is a free choice.
5. One host door, one owner. A taken host door fails with "port is already allocated".
6. The `PORTS` column shows the mapping: `0.0.0.0:5433->5432/tcp` means "door 5433, on all
   addresses of this Mac, forwards to container door 5432". `0.0.0.0` means "every address
   this machine has". The `[::]` copy is the same rule in the newer address format (IPv6).

### Example — real commands, real output from this machine

`nginx` is a tiny web server program serving a welcome page; inside its container it listens
on door 80. `curl` fetches a web page from an address. Together they make port mapping
visible.

**Step 1 — the pain: no `-p` at all.**

```bash
docker run -d --name hidden nginx:alpine
curl localhost:80
```

```text
curl: (7) Failed to connect to localhost port 80 after 0 ms: Couldn't connect to server
```

The container is `Up`, nginx is healthy — but no door on the Mac leads to it.

**Step 2 — the fix.**

```bash
docker run -d --name web -p 8080:80 nginx:alpine
curl localhost:8080
```

```text
<!DOCTYPE html>
...
<h1>Welcome to nginx!</h1>
...
```

Mac door 8080 → container door 80 → nginx → back out. (Aside: a `curl` fired in the same
split second as the start got `curl: (52) Empty reply` — the container was up before nginx
inside was ready. One second later it answered. Containers start fast, not instantly.)

**Step 3 — the trap: swapped sides. It starts without error.**

```bash
docker run -d --name backwards -p 80:8080 nginx:alpine
```

```text
$ docker ps --filter name=backwards
PORTS                                             NAMES
80/tcp, 0.0.0.0:80->8080/tcp, [::]:80->8080/tcp   backwards
```

(The lone `80/tcp` means: the image declares "my program listens on 80 inside", and that door
is unmapped. A hint, if you know to look.) Both knocks now fail, differently:

```text
$ curl localhost:8080
curl: (7) Failed to connect to localhost port 8080 after 0 ms: Couldn't connect to server

$ curl localhost:80
curl: (52) Empty reply from server
```

| Error | Plain meaning |
|---|---|
| `curl: (7) Couldn't connect` on 8080 | No door 8080 exists on the Mac. None was opened. |
| `curl: (52) Empty reply` on 80 | Door 80 exists and forwards — into container door 8080, where nobody listens. The knock went through and died inside. |

The wrong order does not fail at start. It fails at connection time, with confusing errors.

**Step 4 — the taken door.** The `pg` container holds Mac door 5432, so:

```bash
docker run -d --name clash -p 5432:80 nginx:alpine
```

```text
docker: Error response from daemon: failed to set up container networking: ...
Bind for 0.0.0.0:5432 failed: port is already allocated
```

| Error text | Plain meaning |
|---|---|
| `Bind for 0.0.0.0:5432 failed` | Docker tried to open Mac door 5432. |
| `port is already allocated` | That door already has an owner. One door, one owner. |

Fix: pick a different **host** number, leave the container side alone. This is exactly why
the task maps `-p 5433:5432`.

**Step 5 — cleanup.** `docker rm -f hidden backwards clash`. Note: `clash` had to be removed
too — the failed run still created the container; it just never started.

### Important terms

| Term | Meaning |
|---|---|
| Port | A numbered door on a machine. |
| `-p HOST:CONTAINER` | Mac door HOST forwards to container door CONTAINER. Host first. |
| `0.0.0.0` | Every address this machine has. |
| `port is already allocated` | The host door already has an owner. Pick another host number. |
| `curl: (7) Couldn't connect` | No door at that address: nothing listens there. |
| `curl: (52) Empty reply` | The door exists but leads to a place where nobody listens. |

### Common mistakes

- **Swapping the sides.** Starts clean, fails at connect time. Host first, container second.
- **Forgetting `-p` entirely.** `Up` and healthy, yet unreachable. If a connection fails,
  check the `PORTS` column first.
- **Fighting "port is already allocated".** Just pick another host number. The host side is a
  free choice.
- **"Fixing" the container side to match.** `-p 5433:5433` fails: inside the container,
  Postgres still listens on 5432. The container side is the program's fixed door, never a
  choice.

### Where it is used

- This task: `-p 5433:5432`, then `pgcli` to `localhost:5433`.
- The Spring app's datasource URL carries the host-side number.
- Two Postgres versions side by side: host 5432 and host 5433. Inside, both listen on 5432 —
  separate walls, no conflict.
- Every web app ever run locally.

### Link to the task

Subtask 1 needs a mapped port. Subtask 2 is this rule made permanent: `-p 5433:5432` on
purpose. Checkpoint question 2, word for word: in `-p 5433:5432`, 5433 is the Mac, 5432 is
inside the container.

### Do it yourself

```bash
docker pull nginx:alpine                          # ~20 MB download, first time only

docker run -d --name hidden nginx:alpine          # no -p on purpose
curl localhost:80                                 # expect: curl: (7) ... Couldn't connect

docker run -d --name web -p 8080:80 nginx:alpine
curl localhost:8080                               # expect: HTML with "Welcome to nginx!"
docker ps                                         # expect PORTS: 0.0.0.0:8080->80/tcp

docker run -d --name clash -p 8080:80 nginx:alpine
# expect: FAILS — "Bind for 0.0.0.0:8080 failed: port is already allocated" (web owns it)

docker rm -f web clash                            # clash exists even though it never started

docker run -d --name backwards -p 80:8080 nginx:alpine   # swapped on purpose — starts fine!
curl localhost:8080                               # expect: curl: (7) Couldn't connect
curl localhost:80                                 # expect: curl: (52) Empty reply from server

docker rm -f hidden backwards                     # cleanup
```

**Done when:** all three failures produced — no door, taken door, swapped doors — and the
rule is memorized: host first, container second.

### Summary for revision

- `-p HOST:CONTAINER`: Mac door first, container door second. Always.
- No `-p` → healthy but unreachable. `curl: (7)`.
- Swapped `-p` → starts clean, fails at connect time. `curl: (52)` through the door.
- Taken host door → "port is already allocated". Pick another host number.
- The container side is fixed by the program (Postgres: 5432). The host side is yours.
- `PORTS` column: `0.0.0.0:5433->5432/tcp` = Mac 5433 → container 5432.

### Check questions, with answers

**Question 1.** A Postgres container is started with `-p 5433:5432`. Postgres, inside its
container, listens on door 5432.
(a) Which of the two numbers is a door on your Mac, and which is a door inside the container?
(b) You open `pgcli` on your Mac. Which number goes into the connection address?
(c) A program running *inside* that same container wants to talk to Postgres. Which door does
it use?

**Answer.**
(a) 5433 is the Mac's door. 5432 is the container's door. Host first, container second.
(b) 5433. `pgcli` runs on the Mac, and Mac programs knock on Mac doors.
(c) 5432, directly. Inside the wall, programs use the container's own doors. The mapping only
exists for knocks coming from outside.

**Question 2.** A friend runs this; the password part is correct:

```bash
docker run -d --name db -e POSTGRES_PASSWORD=dev -p 5432:5433 postgres:17-alpine
```

The container shows `Up`. But every connection from the Mac to `localhost:5432` dies with an
"empty reply"-style error. `docker ps` shows `PORTS` as `0.0.0.0:5432->5433/tcp`.
(a) What exactly is wrong?
(b) Write the corrected `-p` flag.

**Answer.**
(a) The sides are swapped. Mac door 5432 forwards to container door 5433 — but Postgres
inside listens on 5432, so the knock dies inside the container. The container shows `Up`
because a swapped mapping never fails at start, only at connection time.
(b) `-p 5432:5432` — or any free Mac door on the left, like `-p 5433:5432`. The right side
must stay 5432, because that is the door Postgres actually listens on inside.

### Your doubts

None in this lesson.

---

## Lesson 6 — The first real run, and reading every `docker ps` column

### Definition

The standard server-start command: the four known flags assembled into one line. And
`docker ps` as the instrument panel: seven columns, each readable.

```bash
docker run -d --name pg17 -e POSTGRES_PASSWORD=dev -p 5433:5432 postgres:17-alpine
```

### Why it is needed

This is subtask 1, word for word. Every service in every later phase — Redis, Kafka, the app
itself — starts with this exact shape. `docker ps` is the first command of every working day:
what is running, and on which doors.

### How it works, step by step

| Part | Meaning |
|---|---|
| `docker run` | Create a brand-new container and start it. |
| `-d` | Detached: run in the background, print the ID, return the terminal. |
| `--name pg17` | The handle for every later command. |
| `-e POSTGRES_PASSWORD=dev` | One environment variable. The start-up script reads it on first start and sets the superuser password. |
| `-p 5433:5432` | Mac door 5433 forwards to container door 5432. Host first, container second. |
| `postgres:17-alpine` | The image: Postgres 17 on a tiny Alpine Linux. |
| *(nothing after the image)* | The image's default main command runs: the start-up script that launches the server. |

Flag order among the flags does not matter. All flags before the image name.

**Naming note.** The task's text says `docker inspect pg`, but on this machine the name `pg`
belongs to the F5.1 container, which owns Mac door 5432 and may hold practice tables from the
SQL tasks. It stays untouched. This task's container is `pg17` on door 5433. Wherever the
task says `pg`, read `pg17`.

### Example — real commands, real output from this machine

```text
$ docker run -d --name pg17 -e POSTGRES_PASSWORD=dev -p 5433:5432 postgres:17-alpine
dfe0b0b544d82b809fc5bfa91278c21958f3ec9b06889590da7b8e8cf83cd18f

$ docker ps
CONTAINER ID   IMAGE                COMMAND                  CREATED         STATUS          PORTS                                         NAMES
dfe0b0b544d8   postgres:17-alpine   "docker-entrypoint.s…"   4 seconds ago   Up 3 seconds    0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp   pg17
e931f86c4496   postgres:17-alpine   "docker-entrypoint.s…"   3 weeks ago     Up 13 minutes   0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp   pg
```

Every column, left to right:

| Column | What it tells you |
|---|---|
| `CONTAINER ID` | First 12 characters of the 64-character ID printed at creation. |
| `IMAGE` | The template this copy came from. Both rows: `postgres:17-alpine`. One image, two containers. |
| `COMMAND` | The main command, shortened. `docker-entrypoint.s…` is the start-up script; it reads the `-e` values, then launches the server. `docker ps --no-trunc` shows it in full. |
| `CREATED` | When the container was created. |
| `STATUS` | `Up X` = main command alive, and for how long. Resets on every restart — `pg` was created 3 weeks ago but restarted 13 minutes ago. |
| `PORTS` | The doors. `0.0.0.0:5433->5432/tcp`: Mac door 5433 → container door 5432. `[::]` is the same rule in the newer address format. |
| `NAMES` | The handle. |

The picture as a whole: two Postgres servers, same image, side by side without conflict.
Inside, both listen on 5432 — separate walls. Outside, different Mac doors: 5432 and 5433.

### Important terms

| Term | Meaning |
|---|---|
| `docker-entrypoint.s…` | The Postgres image's start-up script: reads `-e` values, launches the server. |
| `--no-trunc` | `docker ps --no-trunc`: show full values instead of shortened ones. |
| `Up` vs ready | `Up` means the main command is alive. On first start, Postgres needs a few seconds before it accepts connections. |

### Common mistakes

- **Name taken** → `Conflict` error. **Host door taken** → `port is already allocated`.
  Fix: remove the old container, or pick another name or door.
- **Forgetting `-e POSTGRES_PASSWORD`.** An ID prints — and the container dies instantly with
  the refusal. `ps` shows nothing. Habit: when a container is missing from `ps`, look for its
  corpse in `ps -a`.
- **Trusting `Up 1 second` to mean "ready".** `Up` means alive, not ready. Readiness is in
  the logs (Lesson 8).
- **Doubting the port order later.** Read the `PORTS` column: the arrow points from Mac to
  container.

### Where it is used

- Redis (Lesson 17): `docker run -d --name redis -p 6379:6379 redis:7-alpine` — same shape.
- Kafka and the app's own image later: same shape again.
- This command becomes muscle memory.

### Link to the task

- Subtask 1 — solved: Postgres detached, named, passworded, port-mapped, every column read.
- Subtask 2 — half done: the deliberate `-p 5433:5432` mapping exists. `pgcli` is Lesson 7.
- Subtask 3 — one of six daily commands done: `docker ps`.

### Do it yourself

```bash
docker run -d --name pg17 -e POSTGRES_PASSWORD=dev -p 5433:5432 postgres:17-alpine
# expect: a 64-character ID, prompt back instantly
# (first time only: the image downloads ~100 MB before the ID appears)

docker ps
# expect one row: pg17 | postgres:17-alpine | "docker-entrypoint.s…" | Up X seconds |
#                 0.0.0.0:5433->5432/tcp | pg17
```

Leave `pg17` running — the next lessons connect to it.

**Done when:** `docker ps` shows `pg17` as `Up` with `0.0.0.0:5433->5432/tcp`, and every one
of the seven columns can be explained out loud.

### Summary for revision

- The server-start shape: `docker run -d --name <n> -e <setting> -p <mac>:<container> <image>`.
- Columns: ID (12 of 64) · IMAGE (template) · COMMAND (main command) · CREATED (born) ·
  STATUS (`Up` since last start) · PORTS (Mac door → container door) · NAMES (handle).
- `CREATED` never changes; `STATUS` resets on restart.
- Two servers from one image coexist: different Mac doors, same inside door.

### Check questions, with answers

**Question 1.** Redis is a server program that keeps data in memory. Inside its container, it
listens on door 6379. You run:

```bash
docker run -d --name cache -p 6380:6379 redis:7-alpine
```

(a) After pressing enter, is your terminal held, or free — and what gets printed?
(b) A program on your Mac wants to talk to this Redis. Which door number does it use?
(c) What will the `PORTS` column show for `cache`?

**Answer.**
(a) Free. `-d` prints the new container's 64-character ID and returns the prompt.
(b) 6380 — the Mac-side door. Mac programs knock on Mac doors.
(c) `0.0.0.0:6380->6379/tcp` (plus the `[::]` twin): Mac door 6380 forwards to container door
6379.

**Question 2.** On some machine, `docker ps` shows this single row:

```text
CONTAINER ID   IMAGE                COMMAND                  CREATED      STATUS        PORTS                      NAMES
9f3c21aa07bd   postgres:17-alpine   "docker-entrypoint.s…"   2 days ago   Up 6 hours    0.0.0.0:5434->5432/tcp     db2
```

Explain the row: where did this container come from, is it alive and since when, why do
`CREATED` and `STATUS` disagree, and which door would a Mac program knock on to reach it?

**Answer.** The container `db2` was created 2 days ago from the `postgres:17-alpine` image.
It is alive: its main command — the start-up script that launched the server — has been
running for 6 hours. `CREATED` and `STATUS` disagree because the container was stopped and
started again since its creation; `STATUS` counts only since the last start. A Mac program
knocks on door 5434, which forwards to the container's inside door 5432.

### Your doubts

None in this lesson.

---

## Lesson 7 — Connecting with `pgcli`: the five parts of a connection address

### Definition

`pgcli` is a **client**: a terminal program that connects to a Postgres server, sends SQL,
and shows the answers. It takes one argument — a **connection address** (connection URL):

```text
postgresql://postgres:dev@localhost:5433/postgres
```

### Why it is needed

A running server is useless until something talks to it. And the address anatomy is
universal: the Spring datasource URL, IntelliJ's database tab, `psql` — same five parts
everywhere. Every future connection failure names exactly one broken part.

### How it works, step by step

| Part | In our address | Meaning |
|---|---|---|
| Protocol | `postgresql://` | Which language to speak on the wire. Fixed. |
| User | `postgres` | Which account to log in as. `postgres` is the superuser the image created. |
| Password | `dev` | Proof of identity. Came from `-e POSTGRES_PASSWORD=dev`. |
| Host | `localhost` | Which machine to knock on. `localhost` = this Mac. |
| Port | `5433` | Which door on that machine. **The Mac side of `-p 5433:5432`.** |
| Database | `/postgres` | Which database inside the server to open. `postgres` always exists. |

The journey: `pgcli` knocks on `localhost:5433` → Docker forwards to container door 5432 →
the server checks user and password → a session opens on the named database.

### Example — real commands, real output from this machine

**The successful connection:**

```text
$ pgcli postgresql://postgres:dev@localhost:5433/postgres
Server: PostgreSQL 17.11
postgres@localhost:postgres> SELECT version();
+------------------------------------------------------------------------------------------------+
| version                                                                                        |
|------------------------------------------------------------------------------------------------|
| PostgreSQL 17.11 on aarch64-unknown-linux-musl, compiled by gcc (Alpine 15.2.0) 15.2.0, 64-bit |
+------------------------------------------------------------------------------------------------+
postgres@localhost:postgres> \q
Goodbye!
```

The prompt `postgres@localhost:postgres>` shows user, host, database — three of the five
parts, always visible.

**Failure one — a door that does not exist (port 5434):**

```text
connection failed: connection to server at "127.0.0.1", port 5434 failed:
could not receive data from server: Connection refused
```

| What you see | Plain meaning |
|---|---|
| `Connection refused` | The knock found nobody. No door, no server. Wrong *place*. |
| Several lines, two addresses | `localhost` is tried in IPv4 (`127.0.0.1`) and IPv6 (`::1`). One cause, reported per attempt. |

**Failure two — right door, wrong password:**

```text
connection failed: connection to server at "127.0.0.1", port 5433 failed:
FATAL:  password authentication failed for user "postgres"
```

`Connection refused` = the knock died unanswered (wrong place). `FATAL: password
authentication failed` = the server answered and rejected the identity (wrong identity). Two
different repairs.

**Failure three — the silent trap: a wrong port that works.** Mac door 5432 is owned by the
old F5.1 container, with the same password. The connection *succeeds* — into a different
world. `\l` lists all databases on the connected server:

```text
$ pgcli postgresql://postgres:dev@localhost:5432/postgres     ← old pg
postgres@localhost:postgres> \l
| postgres | template0 | template1 | zenith | zenith_lab |

$ pgcli postgresql://postgres:dev@localhost:5433/postgres     ← pg17
postgres@localhost:postgres> \l
| postgres | template0 | template1 |
```

`zenith` and `zenith_lab` are the databases from the SQL tasks, alive in the old container.
The most dangerous port mistake has **no error at all**: valid connection, wrong server. If
tables ever "disappear", check which door was knocked on before panicking.

### Important terms

| Term | Meaning |
|---|---|
| Client | A program that connects to a server, sends questions, reads answers. |
| Connection address (URL) | `postgresql://user:password@host:port/database`. |
| `localhost` | This machine. |
| `postgres` (database) | The default admin database that always exists. |
| `\l` | Client command: list all databases on the connected server. |
| `\q` | Client command: quit. |
| `Connection refused` | Nobody at that door. Wrong place. |
| `FATAL: password authentication failed` | Server reached, identity rejected. |

### Common mistakes

- **Writing the container-side port in the address.** The address belongs to the Mac side of
  `-p`. With `-p 5433:5432`, the address says 5433. Writing 5432 means `Connection refused` —
  or a silent landing on whatever else owns that door.
- **Leaving out the database part.** Run bare, `pgcli` guesses a database named after the Mac
  account, which usually does not exist. Always name it; `/postgres` always exists.
- **Reading the multi-line failure as many errors.** Both address formats are tried; find the
  line that names the cause: `refused` or `FATAL`.
- **Connecting in the first second after `docker run`.** The server may still be
  initializing: one `Connection refused`, then it works. `Up` means alive, not ready.

### Where it is used

- Spring Boot: `spring.datasource.url=jdbc:postgresql://localhost:5433/postgres` — same five
  parts with `jdbc:` in front.
- Every database tool takes this address, as one URL or split across input fields.

### Link to the task

Subtask 2 fully done: port mapped to 5433 on purpose, connection made with the task's exact
address. Checkpoint question 2 now has a lived answer: 5433 is the Mac, 5432 is the
container — both the working door and the trap door were used.

### Do it yourself

Setup: `pgcli --version`. If missing: `brew install pgcli`. `pg17` must be running
(`docker ps`).

```bash
pgcli postgresql://postgres:dev@localhost:5433/postgres
# expect: a prompt like  postgres@localhost:postgres>

SELECT version();        # expect: one-row table, PostgreSQL 17.x
\l                       # expect: exactly three databases: postgres, template0, template1
\q                       # expect: Goodbye!

pgcli postgresql://postgres:dev@localhost:5434/postgres
# expect: Connection refused — no door 5434 exists

pgcli postgresql://postgres:wrong@localhost:5433/postgres
# expect: FATAL: password authentication failed for user "postgres"
```

**Done when:** the version query answered through door 5433, and you can say which failure
means "wrong place" and which means "wrong identity".

### Summary for revision

- Address: `postgresql://user:password@host:port/database`.
- The port is always the Mac side of `-p`.
- Prompt `postgres@localhost:postgres>` = user @ host : database.
- `Connection refused` = wrong place. `FATAL: authentication failed` = wrong identity.
- The worst failure is silent: valid connection, wrong server. `\l` reveals it.

### Check questions, with answers

**Question 1.** Here is a connection address:

```text
postgresql://app_user:s3cret@localhost:5433/orders
```

(a) Name what each of the five choosable parts is.
(b) The container behind it was started with `-p 5433:5432`. Why does the address say 5433
and not 5432?

**Answer.**
(a) User: `app_user`. Password: `s3cret`. Host: `localhost` (this Mac). Port: `5433` (the
Mac door). Database: `orders`.
(b) The address is used by a Mac program, and Mac programs knock on Mac doors. 5433 is the
Mac side of the mapping. 5432 is the container's inside door, reachable only through the
mapping, never named in the address.

**Question 2.** Three failures. For each, name the address part to check first, and why:

(i) `connection to server at "127.0.0.1", port 5434 failed: Connection refused`
(ii) `FATAL: password authentication failed for user "postgres"`
(iii) The connection succeeds, no error anywhere — but the tables created yesterday are
missing.

**Answer.**
(i) The port (then the host). `Connection refused` means nobody at that door: wrong port
number, or the server is not running. Check `docker ps` and its `PORTS` column.
(ii) The password (then the user). The server was reached and rejected the identity.
(iii) The port (then the database). A silent success with missing data means the wrong
server answered — another Postgres on another door — or the wrong database was opened.
Check `docker ps` for other Postgres containers and `\l` on the connection.

### Your doubts

None in this lesson.

---

## Lesson 8 — `docker logs -f`: the container's diary

### Definition

`docker logs <name>` prints everything the container's main command has ever written.
`docker logs -f <name>` (follow) prints it, then stays open, streaming every new line live.
Ctrl-C while following stops the watching. The container never notices.

### Why it is needed

A detached container prints nothing to the screen. Without `logs`: blind. Is the server
ready? Why did a container die? What is the app doing right now? The log is the container's
diary, and `docker logs` opens it.

### How it works, step by step

1. Programs write lines of text as they work: normal output and error output.
2. Foreground run: the terminal shows them. Detached run: Docker captures and stores every
   line with the container.
3. `docker logs <name>` prints the whole diary, from creation until now. It works on
   **stopped** containers too — a corpse keeps its diary. The diary dies only with
   `docker rm`.
4. `-f` keeps the view open: new lines appear the moment the program writes them.
5. Ctrl-C during `-f` closes the view. Nothing inside the container changes.
6. Extras: `--tail 20` = last 20 lines only. `--since 5m` = last five minutes only.

### Example — real commands, real output from this machine

**Step 1 — the vanished "bye", found.**

```text
$ docker run -d --name whisper alpine echo "bye"
1d4393c61b3054a5f8b3390933567a7a907068fa1f03146339211c3c0bb44ead

$ docker logs whisper
bye
```

The container exited instantly; its diary kept the word. Nothing is lost until `docker rm`.

**Step 2 — `pg17`'s diary.**

```text
$ docker logs pg17
The files belonging to this database system will be owned by user "postgres".
...
2026-11-18 07:35:49 UTC [1] LOG:  listening on IPv4 address "0.0.0.0", port 5432
2026-11-18 07:35:49 UTC [1] LOG:  database system is ready to accept connections
2026-11-18 07:38:50 UTC [66] FATAL:  password authentication failed for user "postgres"
```

| Line | What it tells you |
|---|---|
| First-start lines (`The files belonging to...`) | The start-up script building a fresh database. Happens once. |
| `listening on ... port 5432` | The server listens on the **container door**. It has no idea door 5433 exists. The mapping is Docker's business, outside the wall. |
| `database system is ready to accept connections` | The readiness line. `Up` means alive; this line means ready. |
| `FATAL: password authentication failed` | The server's side of the wrong-password attempt from Lesson 7, with timestamp. |

Note: a few early lines print out of order — normal output and error output are two streams
that Docker interleaves. Timestamps tell the true order.

**Step 3 — follow live.** `docker logs -f pg17` printed the diary and held. A fresh
wrong-password attempt from a second terminal appeared in the follow window the same second:

```text
2026-11-18 07:42:16 UTC [76] FATAL:  password authentication failed for user "postgres"
```

**Step 4 — Ctrl-C is harmless.** After stopping the follow:

```text
$ docker ps --filter name=pg17
... STATUS Up 6 minutes ... pg17
```

Watching ended. Container unbothered.

### Important terms

| Term | Meaning |
|---|---|
| `docker logs <n>` | Print the container's whole diary, since creation. Works on stopped containers. |
| `-f` (follow) | Keep streaming new lines live. Ctrl-C stops only the watching. |
| `--tail N` | Only the last N lines. |
| Readiness line | `database system is ready to accept connections` — ready, not merely `Up`. |

### Common mistakes

- **Fearing Ctrl-C during `-f` kills the container.** It only closes the view.
- **Looking at the terminal for a detached container's output.** It goes to the diary.
- **Thinking a stopped container's logs are gone.** The diary outlives the stop; it dies with
  `docker rm`.
- **Printing a week of diary when the last screen is needed.** `docker logs --tail 20` is the
  daily form.

### Where it is used

- After every server start: wait for the readiness line before wiring the app.
- Autopsies: `Exited (1)` → read the corpse's diary for the cause.
- Later phases: the Spring Boot app in a container is watched with `docker logs -f`.
- Task F7.9 reads health status; logs hold the traces.

### Link to the task

Subtask 3 — the six daily commands: `ps` done, `logs -f` done. Four remain: `exec`, `stop`,
`rm`, `images`.

### Do it yourself

`pg17` must be running. Two terminal windows needed.

```bash
docker run -d --name whisper alpine echo "bye"   # expect: an ID, nothing else
docker logs whisper                              # expect: bye
docker rm whisper

docker logs pg17                                 # expect: boot diary; find the readiness line

# Window 1:
docker logs -f pg17                              # prints diary, then holds, streaming

# Window 2:
pgcli postgresql://postgres:wrong@localhost:5433/postgres
# expect in window 1, same second: FATAL: password authentication failed

# Window 1: Ctrl-C
docker ps                                        # expect: pg17 still Up
```

**Done when:** "bye" found in a dead container's diary, the readiness line found, a FATAL
watched appearing live, and `pg17` survived Ctrl-C.

### Summary for revision

- The diary holds everything the main command ever wrote, since creation.
- Detached output goes only to the diary. Stopped containers keep theirs.
- `-f` streams live; Ctrl-C ends the view, never the container.
- The server logs its side of every failure — with timestamps.
- `Up` = alive. The readiness line = ready.
- Daily form: `docker logs --tail 20 <name>`.

### Check questions, with answers

**Question 1.** You run:

```bash
docker run -d --name greeter alpine echo "good morning"
```

An ID prints, and nothing else. Name the two commands: one that proves the container existed
and finished without error, and one that shows the text it printed. Say what each outputs.

**Answer.** `docker ps -a` proves it: a row for `greeter` with STATUS `Exited (0)` — it
existed, ran, ended without error. `docker logs greeter` shows the text: it prints
`good morning`, kept in the diary even though the container is stopped.

**Question 2.** A Postgres container named `db` shows `Up 10 minutes`. The app on the Mac
cannot connect. `docker logs db` ends with:

```text
2026-11-18 08:00:00 UTC [1] LOG:  database system is ready to accept connections
```

Is the problem more likely inside the container, or outside it — and what next?

**Answer.** Outside. The readiness line proves the server inside is alive and accepting
connections, so the container's world is healthy. Check the path to it: the `PORTS` column
(does a mapping exist, right way round?) and the connection address (host port — the Mac
side, host, user, password).

### Your doubts

None in this lesson.

---

## Lesson 9 — `docker exec -it`: a shell inside the running container

### Definition

`docker exec <name> <command>` runs one **extra** command inside an already-running
container, next to its main command. `docker exec -it <name> bash` makes it interactive —
and when the extra command is a shell, you stand inside the container, typing.

### Why it is needed

Some questions can only be answered from inside: is the data folder there? what Linux is
this? what does the server see? From outside you guess; from inside you look. Practical
prize: inside lives `psql`, Postgres's own client — no door, no mapping, no password. When
the network path is broken, `exec` is the console cable.

### How it works, step by step

1. `exec` needs a **running** container. It cannot enter a corpse.
2. `docker exec <name> <command>` starts the extra command inside the container's world: its
   files, its Linux, its users. Output comes back. The main command keeps running, untouched.
3. `-i` keeps typing flowing in. `-t` gives the session a real terminal. `-it` = a prompt to
   live in.
4. Ending the extra command (`exit`, or it finishing) changes nothing for the container. Only
   the **main** command's death stops a container.
5. Ask for `bash`. The `postgres:17-alpine` image ships it. Bare `alpine` ships only `sh`.

### Example — real commands, real output from this machine

**Step 1 — a whole Linux in there.**

```text
$ docker exec pg17 cat /etc/os-release
NAME="Alpine Linux"
VERSION_ID=3.24.3
PRETTY_NAME="Alpine Linux v3.24"

$ docker exec pg17 bash -c 'whoami && hostname'
root
dfe0b0b544d8
```

Alpine Linux inside; the user is `root`; the hostname is the container's own ID — the same
string `docker ps` shows. The container thinks it is a computer named after itself.

**Step 2 — the interactive way in.**

```text
$ docker exec -it pg17 bash
dfe0b0b544d8:/#
```

The prompt changes. Every command now runs inside the container.

**Step 3 — `psql`, and a table.**

```text
dfe0b0b544d8:/# psql -U postgres
psql (17.11)
postgres=# CREATE TABLE visitors(id int, name text);
CREATE TABLE
postgres=# INSERT INTO visitors VALUES (1, 'first'), (2, 'second');
INSERT 0 2
postgres=# SELECT * FROM visitors;
 id |  name
----+--------
  1 | first
  2 | second
(2 rows)
```

No password was asked. The boot diary explains it: `initdb: warning: enabling "trust"
authentication for local connections`. Connections from inside the container are local, and
local is trusted. `pgcli` from the Mac needed the password because it came through the door,
from outside. Same server, two treatments.

**Step 4 — leave twice, check the pulse.**

```text
postgres=# \q
dfe0b0b544d8:/# exit
$ docker ps --filter name=pg17
... STATUS Up 9 minutes ... pg17
```

`\q` ends `psql`; `exit` ends `bash`. Both were extra commands. The server never blinked.

**Step 5 — the error on a stopped container.**

```text
$ docker exec parked ls
Error response from daemon: container 354d44023c48... is not running
```

| Error text | Plain meaning |
|---|---|
| `container ... is not running` | `exec` places a command next to the main command. No running main command, nowhere to stand. `docker start` it first. |

### Important terms

| Term | Meaning |
|---|---|
| `docker exec` | Run an extra command inside a running container. |
| `-i`, `-t`, `-it` | Keep input open; give a terminal; together: an interactive session. |
| Extra command vs main command | Extra commands come and go freely. Only the main command's death stops the container. |
| `psql` | Postgres's own client, pre-installed inside the image. `psql -U postgres` = connect as user postgres. |
| Trust (local connections) | Inside the container, no password is asked. From outside, the password is required. |
| Hostname inside | The container's own ID. |

### Common mistakes

- **`exec` into a stopped container.** `is not running`. `docker start` it first.
- **Forgetting `-it` for a shell.** The shell starts with nobody attached and exits instantly.
  One-shot commands need no `-it`; shells do.
- **Fearing `exit` stops the container.** It ends only the extra command.
- **Mixing up the clients.** `pgcli`: on the Mac, through the mapped door, password required.
  `psql`: inside the container, local, trusted. Both speak SQL to the same server.

### Where it is used

- Lesson 11 inspects the data folder from inside before destroying it.
- Daily debugging: check a config file, list a folder, run `env` inside.
- Later: Kafka topic scripts inside the broker container; `redis-cli` inside Redis.

### Link to the task

- Subtask 4 — solved: shell in, `psql -U postgres`, table created, exited clean.
- Subtask 3 — four of six daily commands: `ps`, `logs`, `exec` done.
- Checkpoint question 4: `docker exec -it <name> bash` — to look around, run the local
  client, and debug from inside.
- The `visitors` table lives in `pg17`'s writable layer. No volume. It is the sacrifice for
  Lesson 11.

### Do it yourself

`pg17` must be running (`docker ps`).

```bash
docker exec pg17 cat /etc/os-release       # expect: NAME="Alpine Linux" ...
docker exec -it pg17 bash                  # expect: prompt changes to <container-id>:/#

hostname                                   # expect: the container ID from docker ps
psql -U postgres                           # expect: postgres=#   — no password asked
CREATE TABLE visitors(id int, name text);  # expect: CREATE TABLE
INSERT INTO visitors VALUES (1, 'first'), (2, 'second');   # expect: INSERT 0 2
SELECT * FROM visitors;                    # expect: 2 rows
\q                                         # back to <container-id>:/#
exit                                       # back to the Mac prompt
docker ps                                  # expect: pg17 STILL Up
```

Keep the `visitors` table — bait for the data-loss experiment.

**Done when:** the table exists, both exits done, `pg17` still `Up`, and you can explain why
`psql` asked no password while `pgcli` from the Mac did.

### Summary for revision

- `exec` = extra command inside a running container. `-it` = interactive shell.
- The hostname inside is the container ID. The user is root. The Linux is Alpine.
- `psql -U postgres` inside: trusted, no password. `pgcli` outside: password required.
- `exit` ends the visit, never the container. Only the main command's death stops it.
- `exec` refuses corpses: `is not running` → `docker start` first.

### Check questions, with answers

**Question 1.** `docker ps -a` shows a container `olddb` with STATUS `Exited (0) 2 hours
ago`. You run:

```bash
docker exec -it olddb bash
```

It fails with `Error response from daemon: container ... is not running`.
(a) Why exactly does `exec` refuse?
(b) Which command makes `olddb` ready for `exec`, without creating anything new?

**Answer.**
(a) `exec` runs an extra command next to the main command. In an `Exited` container the main
command is dead — there is no running world to place the extra command into.
(b) `docker start olddb`. It revives the existing container — same writable layer, nothing
new created. Then `exec` works.

**Question 2.** You are inside a running Postgres container: `docker exec -it pg17 bash`,
then `psql -U postgres`, prompt `postgres=#`. You type `\q` and then `exit`. A friend says:
"you just shut down the database." Is the friend right?

**Answer.** No. `\q` ended `psql` and `exit` ended `bash` — both extra commands. The
container lives exactly as long as its main command, the Postgres server, which never
stopped. Truly stopping it would take `docker stop pg17` — or anything that kills the main
command itself.

### Your doubts

None in this lesson.

---

## Lesson 10 — `docker stop`, `docker rm`, `docker images`: the lifecycle

### Definition

- `docker stop <name>` — politely stop a running container. **Everything is kept.**
- `docker rm <name>` — delete a stopped container. **Everything of it is destroyed.**
- `docker images` — list the templates on the machine. Partner `docker rmi <image>` deletes a
  template.

### Why it is needed

Containers pile up: corpses hold names (`Conflict`), old servers hold doors (`port is
already allocated`), images eat gigabytes. And one distinction decides whether data lives or
dies: **stop keeps the writable layer, rm destroys it.**

### How it works, step by step

**`docker stop`:**
1. Docker sends the main command a polite signal: "please finish up."
2. A cooperative program (Postgres) saves its work and exits cleanly in about a second.
   Status: `Exited (0)`.
3. An ignoring program gets 10 seconds of grace, then is force-killed. Status: `Exited (137)`
   — the force-kill code.
4. Either way: writable layer, diary, and name are kept. `docker start` revives the same
   container, same data.

**`docker rm`:**
5. Works only on stopped containers; refuses running ones. `rm -f` = stop then delete.
6. Destroys the writable layer and the diary, frees the name. The image is untouched.

**`docker images` / `docker rmi`:**
7. `images` lists every template with its disk cost. `rmi` deletes one — but refuses while
   any container, even a stopped one, still uses it.

### Example — real commands, real output from this machine

**Step 1 — stop politely.**

```text
$ docker stop pg17
pg17                   (about 0.2 seconds)

$ docker ps -a --filter name=pg17
... STATUS Exited (0) Less than a second ago ... pg17
```

**Step 2 — revive, and check the table.**

```text
$ docker start pg17
pg17
$ docker exec pg17 psql -U postgres -c "SELECT * FROM visitors;"
 id |  name
----+--------
  1 | first
  2 | second
(2 rows)
```

The table survived. Stop is not delete. The diary confirms: the second boot has no
first-start lines — the data folder already existed, so the start-up script skipped the
build:

```text
$ docker logs --tail 3 pg17
... LOG:  database system was shut down at 2026-11-18 07:47:54 UTC
... LOG:  database system is ready to accept connections
```

**Step 3 — stop a program that ignores the signal.**

```text
$ docker run -d --name stubborn alpine sleep 300
$ docker stop stubborn
stubborn               (took 10.2 seconds)

$ docker ps -a --filter name=stubborn
... STATUS Exited (137) ... stubborn
```

Ten seconds: the grace period for a program that cannot hear the signal. 137: force-killed.

**Step 4 — remove, and what dies with it.**

```text
$ docker rm stubborn
stubborn
$ docker logs stubborn
Error response from daemon: No such container: stubborn
```

The diary died with the container. `rm` is not undoable.

**Step 5 — `rm` refuses running containers.**

```text
$ docker rm pg17
Error response from daemon: cannot remove container "pg17": container is running:
stop the container before removing or force remove
```

**Step 6 — templates, and deleting them.**

```text
$ docker rmi nginx:alpine
Untagged: nginx:alpine
Deleted: sha256:fed7e14be83c...

$ docker rmi postgres:17-alpine
Error response from daemon: conflict: unable to delete postgres:17-alpine (must be forced)
- container e931f86c4496 is using its referenced image eee6c6e1b089
```

`rmi` deleted nginx (no container uses it) and refused Postgres, naming the container that
uses it. The `U` in the `EXTRA` column of `docker images` marks exactly this: in use.

### Important terms

| Term | Meaning |
|---|---|
| `docker stop <n>` | Polite signal → up to 10 s grace → force-kill. Keeps layer, diary, name. |
| `docker start <n>` | Revive the same stopped container, same data. |
| `Exited (0)` vs `Exited (137)` | Clean exit vs force-killed after ignoring the signal. |
| `docker rm <n>` | Delete a stopped container: layer and diary destroyed, name freed. |
| `docker rm -f <n>` | Stop and delete in one step. |
| `docker rmi <image>` | Delete a template. Refused while any container uses it. |

### Common mistakes

- **Thinking `stop` loses data.** It does not — `visitors` survived a stop-start.
- **Thinking `rm` is reversible.** It is not: layer and diary destroyed.
- **`rm` on a running container.** Refused with instructions: stop first, or `rm -f`.
- **`rmi` while containers still use the image.** Refused, naming the user.
- **Confusing the pair.** `rm` deletes containers; `rmi` deletes images.

### Where it is used

- Evening hygiene: stop what is not in use — the machine budget assumes it.
- Freeing a name or door: stop then rm, or `rm -f`.
- Disk reclaim: `docker images`, then `rmi` what no longer earns its megabytes.
- Every data question starts with: was it stopped, or removed?

### Link to the task

- Subtask 3 — complete: all six daily commands used for real (`ps`, `logs -f`, `exec -it`,
  `stop`, `rm`, `images`).
- Checkpoint question 3 armed: `rm` is the destroyer. What should have been used is
  Lessons 11–12.

### Do it yourself

`pg17` with its `visitors` table must exist.

```bash
docker stop pg17                  # ~1 second; prints: pg17
docker ps                         # pg17 absent
docker ps -a                      # pg17  Exited (0)
docker start pg17                 # prints: pg17
docker exec pg17 psql -U postgres -c "SELECT * FROM visitors;"
                                  # expect: 2 rows — STOP KEPT THE DATA

docker run -d --name stubborn alpine sleep 300
docker stop stubborn              # expect: ~10 seconds of waiting
docker ps -a                      # stubborn  Exited (137)
docker rm stubborn                # prints: stubborn
docker logs stubborn              # expect: Error ... No such container

docker rm pg17                    # expect: REFUSED — container is running
docker images                     # templates and disk cost
docker rmi postgres:17-alpine     # expect: REFUSED — a container is using it
```

**Done when:** `visitors` survived a stop-start, `stubborn` took ten seconds and shows 137,
and both refusals appeared with their reasons.

### Summary for revision

- `stop` = polite pause. Layer, diary, name all kept. `start` revives.
- Cooperative main command → `Exited (0)` fast. Ignoring one → 10 s, then `Exited (137)`.
- `rm` = true deletion: layer and diary gone, name freed, image untouched. Not undoable.
- `rm` refuses running containers; `rm -f` stops and deletes.
- `images` lists templates; `rmi` deletes one, refused while in use (`U` flag).

### Check questions, with answers

**Question 1.** A Postgres container `db` was created with
`docker run -d --name db -e POSTGRES_PASSWORD=dev postgres:17-alpine` — no volume anywhere.
Inside it, a table `orders` was created with three rows.

(a) You run `docker stop db`, then `docker start db`, then look for `orders`. Is it there?
(b) Instead, you run `docker rm -f db`, then the exact same `docker run` command again, and
look for `orders`. Is it there?

**Answer.**
(a) Yes. `stop` keeps the writable layer; `start` revives the same container with the same
storage. `orders` is intact.
(b) No. `rm -f` destroyed that container's storage. The new `docker run` builds a brand-new
container from the read-only image with fresh, empty storage; the first-start script builds a
blank database. `orders` never existed in this new world.

**Question 2.** You run `docker stop slowpoke`. It takes ten full seconds, and afterwards
`docker ps -a` shows:

```text
CONTAINER ID   IMAGE    COMMAND       STATUS                      NAMES
9a8b7c6d5e4f   alpine   "sleep 600"   Exited (137) 5 seconds ago  slowpoke
```

Explain the ten seconds and the 137.

**Answer.** The main command, `sleep 600`, cannot react to the polite stop signal, so nothing
happened; Docker waited its full 10-second grace period, then force-killed the command. 137
is the force-kill exit code. A cooperative program like Postgres exits with code 0 in about a
second instead.

### Your doubts

None in this lesson.

---

## Lesson 11 — Lose data on purpose: `rm` and the empty twin

### Definition

The experiment: a Postgres container with no volume you asked for, holding a real table —
then `docker stop`, `docker rm`, and an identical `docker run`. The table is gone. The rule:
**data dies with the container, unless it lives in storage with a name you chose.**

### Why it is needed

Someday a `docker rm` will hover over a container with months of work inside. Losing data
once, cheaply, on purpose builds the reflex: "where does its data live?" fires before the
enter key. This was the second pass, after Task F5.1, done from memory as the task wanted.

### How it works, step by step

1. The table `visitors` lived in the data folder `/var/lib/postgresql/data` inside `pg17`.
   That folder *is* the database, physically.
2. `docker rm pg17` destroyed the container and freed the name.
3. The identical `docker run` built a **twin**: same image, same flags, same name — fresh,
   empty storage. Nothing connects a new container to a dead one's data. Same command is a
   twin, not a resurrection.
4. The new server found an empty data folder, so the first-start script built a blank
   database. `visitors` never existed in this world.

**The fine print.** The Postgres image carries a baked-in wish: "put
`/var/lib/postgresql/data` in a volume." With no `-v` given, Docker fulfills it with an
**anonymous volume**: a storage area named by a random 64-character hash. The table actually
lived there. `docker rm` does not delete anonymous volumes — it orphans them. This machine
holds 19 such orphans, one per removed Postgres container. The bytes are not erased: they are
nameless, identical-looking, attached to nothing. Practically lost. Survival without a name
is useless — the named volume (Lesson 12) is the fix.

### Example — real commands, real output from this machine

**Step 1 — look at what is about to die.**

```text
$ docker exec pg17 ls /var/lib/postgresql/data
PG_VERSION
base
global
pg_wal
postgresql.conf
...
```

**Step 2 — the kill.**

```text
$ docker stop pg17
pg17
$ docker rm pg17
pg17
$ docker ps -a --filter name=pg17
CONTAINER ID   IMAGE     COMMAND   CREATED   STATUS    PORTS     NAMES
```

**Step 3 — the identical command, again.**

```text
$ docker run -d --name pg17 -e POSTGRES_PASSWORD=dev -p 5433:5432 postgres:17-alpine
bba7f83386f5...      (old ID was dfe0b0b5... — a different being)
```

**Step 4 — the money shot.**

```text
$ docker exec pg17 psql -U postgres -c "SELECT * FROM visitors;"
ERROR:  relation "visitors" does not exist
LINE 1: SELECT * FROM visitors;
                      ^
```

| Error text | Plain meaning |
|---|---|
| `relation "visitors" does not exist` | "Relation" is Postgres-speak for table. This server has never heard of it. |

**Step 5 — the orphan, located.**

```text
$ docker inspect -f '{{json .Mounts}}' pg17
[{"Type":"volume","Name":"2282437fe9c1...","Destination":"/var/lib/postgresql/data",...}]

$ docker volume ls | grep be3fe53a9f80
local     be3fe53a9f8005e158702b390fb5ceea4576dfb688cb60d8f90bdf7657cc7ed6
```

The new container writes into a new hash. The old hash still exists — holding `visitors`,
attached to nothing, indistinguishable from its neighbours. An unmarked grave.

### Important terms

| Term | Meaning |
|---|---|
| Twin, not resurrection | An identical `docker run` shares only the image with the dead container. |
| Anonymous volume | Auto-created storage with a random hash name, made when an image asks for a volume and no `-v` was given. |
| Orphan | An anonymous volume left behind by `docker rm`. Nameless, practically unrecoverable. |
| `relation` | Postgres-speak for table. |

### Common mistakes

- **"The same run command will bring my data back."** Twin, not resurrection.
- **Blaming `docker stop`.** Stop keeps everything; the destroyer is `rm`.
- **Planning to recover from the orphan volume.** Treat orphaned data as gone.
- **Running the experiment on data you care about.** Throwaway tables only.

### Where it is used

- The pre-`rm` reflex: "where does its data live, and does that place have a name?"
- CI pipelines exploit it: every test run gets a factory-new database for free.
- Disk hygiene: orphan volumes are why Docker's disk use grows mysteriously (cleanup in
  Lesson 13).

### Link to the task

- Subtask 5 — solved, from memory, second pass, with real loss.
- Checkpoint question 3: "What should you have used?" — storage with a name: a **named
  volume** (Lesson 12).

### Do it yourself

The victim is `pg17` with its `visitors` table. Throwaway data only.

```bash
docker exec pg17 ls /var/lib/postgresql/data     # the database's physical home
docker stop pg17                                 # prints: pg17
docker rm pg17                                   # prints: pg17
docker ps -a                                     # pg17 absent

docker run -d --name pg17 -e POSTGRES_PASSWORD=dev -p 5433:5432 postgres:17-alpine
sleep 3
docker exec pg17 psql -U postgres -c "SELECT * FROM visitors;"
# expect: ERROR:  relation "visitors" does not exist

docker volume ls                                 # the nameless hash volumes — the graves
```

**Done when:** the `ERROR` appeared, and you can say what a new container shares with a
removed one: the image — nothing else.

### Summary for revision

- `rm` + identical `run` = empty twin. Data does not follow the command; it followed the
  container.
- The Postgres image auto-creates an anonymous hash-named volume; `rm` orphans it.
- Orphaned = nameless, unattached, practically lost.
- Stop is safe. Remove is destructive. The fix is a storage name you chose.

### Check questions, with answers

**Question 1.** A Postgres container is created with
`docker run -d --name db -e POSTGRES_PASSWORD=dev -p 5433:5432 postgres:17-alpine`. A table
`invoices` is created inside with ten rows. Then: `docker rm -f db`, followed by the
character-for-character identical `docker run`. A query for `invoices` returns
`ERROR: relation "invoices" does not exist`. Explain why — what do the old and new containers
share, and what do they not share?

**Answer.** They share only the image — the read-only template. They do not share storage:
`rm -f` destroyed the old container and orphaned its anonymous data volume. The identical
command built a twin with a fresh, empty volume; the first-start script built a blank
database. `invoices` never existed in the new world.

**Question 2.** Right after that `docker rm -f db`, you run `docker volume ls` and notice one
more hash-named volume than you can account for.
(a) Where did it come from, given that no `-v` flag was ever used?
(b) Does it still hold the ten `invoices` rows?
(c) Why is the data considered lost anyway?

**Answer.**
(a) The Postgres image declares a built-in volume wish for its data folder. With no `-v`,
Docker created an anonymous hash-named volume for the container automatically.
(b) Yes. `docker rm` leaves anonymous volumes behind; the bytes are still in it.
(c) It has no chosen name, looks identical to every other orphan hash, and is attached to
nothing. Practically unrecoverable — treat as lost.

### Your doubts

None in this lesson.

---

## Lesson 12 — Named volumes: data that outlives its container

### Definition

A **named volume** is a storage area with a name you choose, living outside every container.
`-v pgdata:/var/lib/postgresql/data` mounts the volume named `pgdata` at that path inside the
container. Whatever the program writes there belongs to the volume, not the container.

### Why it is needed

The Lesson 11 grave had no name: the data survived `rm` technically, but nameless and
useless. A name makes storage deliberate: findable, reattachable, backupable. The container
becomes a disposable shell around durable data.

### How it works, step by step

1. `-v NAME:/path` — name left, container path right.
2. Volume missing → Docker **creates it empty, silently**. Volume existing → the new
   container **adopts** its contents.
3. On adopted, non-empty data Postgres skips all first-start work — including
   `-e POSTGRES_PASSWORD`. The old password rules.
4. `docker rm` of the container does not touch the volume.
5. The next container with the same `-v` gets the same world back.
6. **One volume, one running server.** Two servers on one data folder destroy its
   bookkeeping. This rule was paid for on this machine (incident below).

### Example — real commands, real output from this machine

**Step 1 — fresh named volume, plant data.**

```bash
docker run -d --name pg17 -e POSTGRES_PASSWORD=dev -p 5433:5432 \
  -v pgdata:/var/lib/postgresql/data postgres:17-alpine
docker exec pg17 psql -U postgres -c "CREATE TABLE visitors(id int, name text); INSERT INTO visitors VALUES (1,'first'),(2,'second');"
```

```text
CREATE TABLE
INSERT 0 2
```

**Step 2 — the ritual that killed `visitors` in Lesson 11.**

```text
$ docker stop pg17
pg17
$ docker rm pg17
pg17
$ docker volume ls --filter name=pgdata
DRIVER    VOLUME NAME
local     pgdata
```

Container dead and gone. Volume standing, by name.

**Step 3 — new container, same `-v`.**

```text
$ docker run -d --name pg17 -e POSTGRES_PASSWORD=dev -p 5433:5432 -v pgdata:/var/lib/postgresql/data postgres:17-alpine
$ docker exec pg17 psql -U postgres -c "SELECT * FROM visitors;"
 id |  name
----+--------
  1 | first
  2 | second
(2 rows)
```

**Survival.** Same ritual, opposite ending. The difference: storage with a name.

### The incident (real, resolved, nothing lost)

The volume name `pgdata` already existed on this machine — and the old F5.1 container `pg`
was **running on it**. Attaching it to `pg17` put two running Postgres servers on one data
folder. Containers cannot see each other's processes, so Postgres's lock protection failed.
`pg17` "repaired" the live folder; on the next restart of `pg`:

```text
PANIC:  could not locate a valid checkpoint record at 0/4368720
shutting down due to startup process failure
```

| What you see | Plain meaning |
|---|---|
| `PANIC: could not locate a valid checkpoint record` | The folder's bookkeeping was rewritten underneath a live server. The folder is no longer trustworthy. |

Repair: both databases had been dumped to
`F7.8-docker-images-containers-ports-and-volumes/backups/` **before** the restart; `pg` was
rebuilt on a fresh volume `pgdata-f5` and both dumps restored (13 + 3 tables verified, all
rows). The corrupted `pgdata` volume was deleted, freeing the name for the clean run above.
A volume may serve many containers **over time** — never two running servers **at once**.

### Important terms

| Term | Meaning |
|---|---|
| Named volume | Storage with a chosen name, outside all containers. Survives `docker rm`. |
| `-v NAME:/path` | Mount volume NAME at /path inside the container. |
| Adoption | Attaching an existing volume: the new container sees its contents. First-start work is skipped. |
| Created on demand | Unknown volume names are created empty, silently — typos included. |
| One volume, one running server | Never mount one data volume into two running servers. |

### Common mistakes

- **A typo in the volume name** creates a new empty volume silently: a mysteriously blank
  database, no error.
- **Expecting `-e POSTGRES_PASSWORD` to apply to adopted data.** First start only.
- **Mounting one volume into two running servers.** The incident. Corruption.
- **Mixing up the survivals.** Stop-survival = writable layer (no volume needed).
  Rm-survival = named volume only.

### Where it is used

- Every real database container runs with a named volume.
- F7.9's compose file declares the same mechanism in YAML.
- The deliberate absence of a volume is a tool too: CI databases are meant to vanish.

### Link to the task

- Subtask 6 — ritual repeated with `-v pgdata:...`, data survived, `docker volume ls` read.
  `docker volume inspect` completes it in Lesson 13.
- Checkpoint question 3, final answer: a **named volume** mounted at the data folder.

### Do it yourself

```bash
docker rm -f pg17                     # replace the volume-less pg17

docker run -d --name pg17 -e POSTGRES_PASSWORD=dev -p 5433:5432 \
  -v pgdata:/var/lib/postgresql/data postgres:17-alpine
sleep 3
docker exec pg17 psql -U postgres -c "CREATE TABLE visitors(id int, name text); INSERT INTO visitors VALUES (1,'first'),(2,'second');"

docker stop pg17
docker rm pg17
docker volume ls                      # expect: pgdata still listed

docker run -d --name pg17 -e POSTGRES_PASSWORD=dev -p 5433:5432 \
  -v pgdata:/var/lib/postgresql/data postgres:17-alpine
sleep 3
docker exec pg17 psql -U postgres -c "SELECT * FROM visitors;"
# expect: 2 rows — SURVIVED
```

Never mount one volume into two containers that run at the same time.

**Done when:** `visitors` survived `rm` + recreate, and you can name the one difference from
Lesson 11: a `-v` flag pointing at named storage.

### Summary for revision

- `-v NAME:/path`: the volume, not the container, owns the data.
- Missing names are created empty and silently; existing ones are adopted wholesale.
- Adopted data keeps its old password; `-e` is first-start only.
- `rm` kills the container, never the named volume. Reattach to get the world back.
- One volume, one running server. Two running servers = PANIC, corruption.
- Real safety is a dump in a plain file, outside Docker (like `backups/*.sql`).

### Check questions, with answers

**Question 1.** A container is created with
`docker run -d --name db -e POSTGRES_PASSWORD=dev -v appdata:/var/lib/postgresql/data postgres:17-alpine`
(no volume named `appdata` existed before). A table `notes` is created inside. Then
`docker rm -f db`.
(a) Is the `notes` data still on the machine? Where exactly?
(b) A new container starts with `-v appdata:/var/lib/postgresql/data`. Is `notes` visible?
(c) A third container starts with `-v appdat:/var/lib/postgresql/data` — missing "a". What
does it see, and why is there no error?

**Answer.**
(a) Yes — inside the named volume `appdata`, which `rm -f` did not touch.
(b) Yes. The new container adopts `appdata`'s contents; `notes` is there.
(c) A brand-new, blank database. `appdat` did not exist, so Docker silently created it empty,
and Postgres ran a first start on it. No error, because volume names are created on demand.

**Question 2.** A teammate sees container `db1` running with `-v shared:/var/lib/postgresql/data`
and starts `db2` with the same `-v shared:/var/lib/postgresql/data` "to read the same data".
Both now run. What is wrong, what can happen, and what is the rule?

**Answer.** Two running Postgres servers now own one data folder. Each container hides the
other's processes, so Postgres's lock protection cannot work; both rewrite the folder's
bookkeeping, and the folder can become untrustworthy — on this machine that ended in
`PANIC: could not locate a valid checkpoint record`. The rule: one volume, one running server
at a time. Sharing data between services is done by asking the server over the network, never
by sharing its files.

### Your doubts

None in this lesson.

---

## Lesson 13 — Where volume data actually lives: `volume inspect`, and the hidden Linux machine

### Definition

`docker volume ls` lists every volume. `docker volume inspect <name>` prints one volume's
paperwork, including its **Mountpoint** — the folder where the bytes live. On a Mac, that
folder is **not on the Mac**: it is inside a hidden Linux virtual machine.

### Why it is needed

Completes subtask 6, and answers subtask 10 with evidence: where does a named volume
physically store data on macOS? It also explains why Docker "eats disk" invisibly, and why
all containers stopped overnight (the VM stopped).

### How it works, step by step

1. Containers are a Linux feature. macOS cannot run them natively.
2. Docker on a Mac therefore runs **one Linux virtual machine** — a complete computer
   simulated in software. Every container, image, and volume lives inside it. On this Mac the
   VM is managed by **Colima**; Docker Desktop is the other common manager.
3. The Mountpoint from `docker volume inspect` — `/var/lib/docker/volumes/pgdata/_data` — is
   a path **inside the VM**, not on the Mac.
4. `colima ssh` opens a terminal into the VM, where the path exists and the database sits.
5. One table, three addresses: container: `/var/lib/postgresql/data` · VM:
   `/var/lib/docker/volumes/pgdata/_data` · Mac: no address at all.
6. Anonymous volumes live in the same VM folder under hash names. `docker volume prune -f`
   deletes unused anonymous ones. Named volumes are spared, even unused ones.

### Example — real commands, real output from this machine

**Step 1 — the paperwork.**

```text
$ docker volume inspect pgdata
[
    {
        "CreatedAt": "2026-11-18T13:28:02+05:30",
        "Driver": "local",
        "Mountpoint": "/var/lib/docker/volumes/pgdata/_data",
        "Name": "pgdata",
        "Scope": "local"
    }
]
```

**Step 2 — the paradox.**

```text
$ ls /var/lib/docker/volumes/pgdata/_data
ls: /var/lib/docker/volumes/pgdata/_data: No such file or directory
```

The path is real. The machine is wrong.

**Step 3 — the hidden machine.**

```text
$ colima list
PROFILE    STATUS     ARCH       CPUS    MEMORY    DISK      RUNTIME
default    Running    aarch64    2       2GiB      100GiB    docker
```

One simulated Linux computer: 2 processors, 2 GiB memory, 100 GiB disk. Every container ever
run on this Mac ran inside it. The overnight mystery — all containers `Exited` at once — was
this VM shutting down. The 2 GiB is the total budget all containers share (Lesson 17).

**Step 4 — the bytes, found.**

```text
$ colima ssh -- sudo ls /var/lib/docker/volumes/pgdata/_data
PG_VERSION
base
pg_wal
postgresql.conf
...
```

The same folder seen from inside `pg17`, now from the VM side.

**Step 5 — clear the graveyard.**

```text
$ docker volume prune -f
Deleted Volumes:
be3fe53a9f80...   (the Lesson 11 grave, among 19)
Total reclaimed space: 209.2MB

$ docker volume ls
DRIVER    VOLUME NAME
local     pgdata
local     pgdata-f5
local     zenith-stack_pgdata
```

All 19 nameless orphans deleted, 209 MB back. Every named volume survived — including
`zenith-stack_pgdata`, which is attached to nothing. Default `prune` deletes only nameless,
unused volumes. Names protect.

### Important terms

| Term | Meaning |
|---|---|
| Virtual machine (VM) | A complete computer simulated in software. Docker on a Mac runs one Linux VM for everything. |
| Colima | The tool managing that VM on this Mac. Docker Desktop is the common alternative. |
| Mountpoint | The volume's folder path — valid inside the VM. |
| `colima ssh` | A terminal into the hidden VM. |
| `docker volume prune -f` | Delete unused anonymous volumes. Named ones are spared. |

### Common mistakes

- **Searching the Mac for `/var/lib/docker`.** Wrong machine; it exists only inside the VM.
- **Treating volumes as backups.** They survive `docker rm`, not VM deletion
  (`colima delete`, Docker Desktop reset). Real safety: dumps in plain Mac files.
- **Fearing `prune`.** Default prune spares named volumes — proven by the unused
  `zenith-stack_pgdata` surviving. Still, read the deletion list.
- **Forgetting the VM's caps.** Containers see the VM's 2 GiB, not the Mac's full memory.

### Where it is used

- Disk reclaim routine: `docker volume ls` → `prune`, `docker images` → `rmi`.
- Understanding restarts: the VM stopping stops every container at once.
- Backup philosophy: the dump that saved `zenith` lives on the Mac, outside the VM.

### Link to the task

- Subtask 6 — complete: `volume ls` and `volume inspect pgdata` done.
- Subtask 10 — answered: data with no volume lives in the container's writable layer or an
  auto-created anonymous volume, destroyed or orphaned by `docker rm`; a named volume lives at
  `/var/lib/docker/volumes/<name>/_data` on the hidden Linux VM's disk, not in any Mac folder.

### Do it yourself

```bash
docker volume inspect pgdata                   # find the Mountpoint
ls /var/lib/docker/volumes/pgdata/_data        # expect: No such file or directory

# Colima laptops:
colima ssh -- sudo ls /var/lib/docker/volumes/pgdata/_data
# expect: PG_VERSION, base, pg_wal, ...

# Docker Desktop laptops (no colima command) — helper container, pg17 stopped first:
docker stop pg17
docker run --rm -v pgdata:/data alpine ls /data   # expect: PG_VERSION, base, ...
docker start pg17

docker volume prune -f                         # deletes unused nameless volumes only
docker volume ls                               # expect: pgdata still listed
```

**Done when:** the data folder was seen from the VM (or helper container), the Mac path
failed, and `pgdata` survived a prune.

### Summary for revision

- Docker on a Mac = one hidden Linux VM. Everything lives on its disk.
- Mountpoint paths are VM paths. The Mac cannot `ls` them.
- One table, three addresses: container path, VM path, Mac: none.
- Prune deletes nameless unused volumes; named ones survive.
- Volumes survive `rm`, not VM deletion. Dumps in Mac files are real backups.

### Check questions, with answers

**Question 1.** On a Mac, `docker volume inspect appdata` prints
`"Mountpoint": "/var/lib/docker/volumes/appdata/_data"`. Running
`ls /var/lib/docker/volumes/appdata/_data` in the Mac terminal answers
`No such file or directory`. Explain where the folder actually is and why the Mac cannot see
it.

**Answer.** The folder is on the disk of the hidden Linux virtual machine that Docker runs on
every Mac (managed by Colima or Docker Desktop). Containers are a Linux feature, so all of
Docker's storage — images, containers, volumes — lives inside that VM. The Mac's own
filesystem has no `/var/lib/docker` at all, so `ls` fails.

**Question 2.** `docker volume ls` shows five hash-named volumes attached to nothing, a named
volume `pgdata` used by a running container, and a named volume `olddata` attached to
nothing. The user runs `docker volume prune -f`. Which volumes are deleted, which survive,
and why?

**Answer.** The five hash-named ones are deleted: anonymous and unused. `pgdata` survives: it
is named and in use. `olddata` also survives: default prune spares **named** volumes even
when unused. (Proven on this machine: the unused `zenith-stack_pgdata` outlived a prune.)

### Your doubts

None in this lesson.

---

## Lesson 14 — Bind mounts: a window between the Mac and the container

### Definition

A **bind mount** maps a real Mac folder into the container:
`-v /Users/you/folder:/path/inside`. Same `-v` flag as a named volume; the left side decides.
A bare word = named volume. A path starting with `/` or `./` = bind mount.

### Why it is needed

Named volumes are Docker's territory, invisible from the Mac. Sometimes both sides must see
the same files: hand a config or seed script in, get a dump out, edit on the Mac and run
inside. A bind mount is that window.

### How it works, step by step

1. Left-side rule: bare word → named volume. `/...` or `./...` → bind mount.
2. The container sees the Mac folder as its own. Changes on either side appear on the other,
   instantly. It is the same folder, shared through the VM.
3. `:ro` at the end makes the window read-only: the container can look, not touch.
4. A missing Mac path is silently created as an empty folder. A typo means an empty window,
   no error.
5. Data folders → named volumes (fast, managed). Configs and seeds → bind mounts.

### Example — real commands, real output from this machine

Two small tools: `--rm` auto-deletes the container when it exits (one-shot jobs).
`sh -c 'text'` runs the text as a small script inside.

**Step 1 — Mac writes, container reads.**

```text
$ echo "hello from the Mac" > bind-demo/from-mac.txt
$ docker run --rm -v /Users/pavankumar/Projects/foundations/F7.8-docker-images-containers-ports-and-volumes/bind-demo:/window alpine cat /window/from-mac.txt
hello from the Mac
```

**Step 2 — container writes, Mac reads.**

```text
$ docker run --rm -v .../bind-demo:/window alpine sh -c 'echo "hello from the container" > /window/from-container.txt'
$ cat bind-demo/from-container.txt
hello from the container
```

**Step 3 — the read-only window.**

```text
$ docker run --rm -v .../bind-demo:/window:ro alpine sh -c 'echo x > /window/blocked.txt'
sh: can't create /window/blocked.txt: Read-only file system
```

| Error text | Plain meaning |
|---|---|
| `Read-only file system` | `:ro` made the window look-only. The write was refused. |

**Step 4 — the real use: seed scripts.** The Postgres image executes every `.sql` file in
`/docker-entrypoint-initdb.d` on **first start**. One Mac file:

```sql
-- seed/001-init.sql
CREATE TABLE cities(id int, name text);
INSERT INTO cities VALUES (1, 'Pune'), (2, 'Bengaluru'), (3, 'Chennai');
```

```text
$ docker run -d --name pgseed -e POSTGRES_PASSWORD=dev -p 5440:5432 -v .../seed:/docker-entrypoint-initdb.d postgres:17-alpine
$ docker exec pgseed psql -U postgres -c "SELECT * FROM cities;"
 id |   name
----+-----------
  1 | Pune
  2 | Bengaluru
  3 | Chennai
(3 rows)

$ docker logs pgseed 2>&1 | grep initdb.d
/usr/local/bin/docker-entrypoint.sh: running /docker-entrypoint-initdb.d/001-init.sql
```

A brand-new database, pre-filled from a Mac file. (Removed afterwards: `docker rm -f pgseed`.)
The earlier boot-diary line `ignoring /docker-entrypoint-initdb.d/*` meant "folder empty".

### Important terms

| Term | Meaning |
|---|---|
| Bind mount | A real Mac folder mapped into the container. Both sides see the same files. |
| `:ro` | Read-only mount: look, don't touch. |
| `--rm` | Auto-delete the container when it exits. |
| `sh -c 'text'` | Run the text as a small script inside. |
| `/docker-entrypoint-initdb.d` | Postgres magic folder: `.sql` files there run on first start only. |

### Common mistakes

- **One character, two worlds.** `-v pgdata:/x` = named volume. `-v ./pgdata:/x` = bind mount.
- **A typo'd Mac path.** Silently created empty; seed scripts "vanish".
- **Seeding an existing database.** `initdb.d` runs on first start only — same rule as
  `POSTGRES_PASSWORD`. Non-empty data volume → the whole init branch is skipped.
- **Bind-mounting the data folder itself.** Works, but slow and permission-quirky on a Mac.
  Data → named volumes. Configs and seeds → bind mounts.

### Where it is used

- Seed and init scripts — F7.9's compose uses exactly this.
- Config files mounted `:ro`: postgresql.conf, nginx configs, Kafka settings.
- Getting files out: reports, exports, dumps written into a mounted folder.

### Link to the task

Closes the new-words entry: bind mount = "mapping a folder on your machine into the
container. Useful for configuration and seed scripts." Both directions done, `:ro` done, and
the seed pattern done for real.

### Do it yourself

```bash
mkdir -p ~/docker-play/bind-demo ~/docker-play/seed
echo "hello from the Mac" > ~/docker-play/bind-demo/from-mac.txt

docker run --rm -v ~/docker-play/bind-demo:/window alpine cat /window/from-mac.txt
# expect: hello from the Mac

docker run --rm -v ~/docker-play/bind-demo:/window alpine sh -c 'echo "hello from the container" > /window/from-container.txt'
cat ~/docker-play/bind-demo/from-container.txt
# expect: hello from the container

docker run --rm -v ~/docker-play/bind-demo:/window:ro alpine sh -c 'echo x > /window/blocked.txt'
# expect: sh: can't create /window/blocked.txt: Read-only file system

cat > ~/docker-play/seed/001-init.sql <<'EOF'
CREATE TABLE cities(id int, name text);
INSERT INTO cities VALUES (1, 'Pune'), (2, 'Bengaluru'), (3, 'Chennai');
EOF

docker run -d --name pgseed -e POSTGRES_PASSWORD=dev -p 5440:5432 \
  -v ~/docker-play/seed:/docker-entrypoint-initdb.d postgres:17-alpine
sleep 5
docker exec pgseed psql -U postgres -c "SELECT * FROM cities;"
# expect: 3 rows
docker rm -f pgseed
```

**Done when:** files crossed both ways, `:ro` refused a write, and a fresh database came up
pre-seeded from a Mac file.

### Summary for revision

- Bind mount = your Mac folder inside the container. Left side with `/` or `./`.
- Both ways, instantly. `:ro` for look-only.
- Missing paths are created empty, silently.
- Postgres runs `initdb.d` scripts on first start only.
- Data → named volumes. Configs and seeds → bind mounts.

### Check questions, with answers

**Question 1.** Three flags:

```text
(i)   -v pgdata:/var/lib/postgresql/data
(ii)  -v ./seed:/docker-entrypoint-initdb.d
(iii) -v /Users/me/configs:/etc/app:ro
```

For each: named volume or bind mount? Writable or read-only from inside? Name the clue.

**Answer.** (i) Named volume — bare word on the left; writable. (ii) Bind mount — left side
starts with `./`; writable. (iii) Bind mount — left side starts with `/`; read-only, because
of the `:ro` suffix. The clue is always the first characters of the left side.

**Question 2.** A Postgres container has run for weeks on `-v pgdata:/var/lib/postgresql/data`.
The owner adds `-v ./seed:/docker-entrypoint-initdb.d`, recreates the container on the same
`pgdata` volume, and complains: the seed scripts never run — no new tables, no mention in the
log. Explain why, and give two ways to get the tables created.

**Answer.** Seed scripts run on **first start only**, when the data folder is empty. The
adopted `pgdata` volume is non-empty, so the entrypoint skips the whole first-start branch —
passwords and seeds alike — without even reading `initdb.d`. Two fixes: (a) run the SQL
against the live server directly (`docker exec -i <n> psql -U postgres < seed/001-init.sql`,
or paste it in `pgcli`); (b) start the container on a fresh, empty volume (or none), so a
true first start happens and the seeds execute.

### Your doubts

None in this lesson.

---

## Lesson 15 — The bridge network: the private street containers live on

### Definition

A Docker **network** is a private network containers attach to. The default one, **`bridge`**,
is where every plain `docker run` container lands. Each container gets its **own IP address**
there — its house number on a private street.

### Why it is needed

It is the "wall" from the port-mapping lesson, seen properly: Mac tools cannot reach container
IPs, and `-p` punches deliberate doors through. And containers need to talk to **each other**
— in Task F7.9 the app container talks to the Postgres container. That happens inside the
network, with no `-p` involved.

### How it works, step by step

1. `docker network ls` shows three built-ins: `bridge` (default private street), `host` (no
   wall — share the machine's network), `none` (no network at all).
2. Plain `docker run` attaches the container to `bridge`.
3. Each container gets a private IP like `172.17.0.2`, handed out in start order. The street
   is `172.17.0.0/16`; `172.17.0.1` is the **gateway**, the exit toward the outside world.
4. Same-network containers reach each other directly, by IP, on **container doors**. No `-p`
   — mappings exist only for crossing the wall from the Mac.
5. From the Mac, these IPs are unreachable: the street lies inside the hidden Linux VM.
6. IPs are unstable (start order). Finding containers **by name** needs a custom network —
   exactly what compose builds in Task F7.9.

### Example — real commands, real output from this machine

**Step 1 — the networks.**

```text
$ docker network ls
NETWORK ID     NAME      DRIVER    SCOPE
06fe85a17317   bridge    bridge    local
847e9bf7caf1   host      host      local
67853f34c868   none      null      local
```

**Step 2 — the street and its residents** (from `docker network inspect bridge`):

```text
Subnet: 172.17.0.0/16      Gateway: 172.17.0.1

Containers:
pg   -> 172.17.0.2
pg17 -> 172.17.0.3
```

The task's promised sight: the container with its own IP on the bridge network.

**Step 3 — one container talks to the other, by IP.**

```text
$ docker exec -e PGPASSWORD=dev pg17 psql -h 172.17.0.2 -U postgres -d zenith -c "SELECT count(*) AS venues FROM venue;"
 venues
--------
     23
(1 row)
```

`pg17`'s `psql` crossed the street into `pg`. Note: container door 5432 (mappings do not
exist inside), and a password (network connections are not "local", so trust does not apply).

**Step 4 — any container can do it.**

```text
$ docker run -d --name buddy alpine sleep 120
$ docker exec buddy ping -c 2 172.17.0.3
2 packets transmitted, 2 packets received, 0% packet loss
round-trip min/avg/max = 0.067/0.102/0.138 ms
```

**Step 5 — the Mac is not on the street.**

```text
$ ping -c 1 -t 2 172.17.0.3
1 packets transmitted, 0 packets received, 100.0% packet loss
```

The same address that answered in a tenth of a millisecond inside is silent from the Mac.

### Important terms

| Term | Meaning |
|---|---|
| `bridge` | The default private network for plain `docker run` containers. |
| Container IP | The container's house number on the street, e.g. `172.17.0.3`. Assigned by start order. |
| Gateway | The street's exit toward the outside world (`172.17.0.1`). |
| `host` network | No wall: the container shares the machine's network directly. |
| `none` network | No network at all. |

### Common mistakes

- **`localhost` inside a container means the container itself** — not the Mac, not other
  containers. A containerized Spring app with `jdbc:postgresql://localhost:5432/...` knocks
  on its own door. The number-one containerized-app config bug.
- **Hard-coding bridge IPs in configs.** House numbers shuffle with start order. Names on a
  custom network are the stable way (Task F7.9).
- **Expecting `172.17.0.x` to work from the Mac.** It cannot. Use the `-p` doors.
- **Thinking `-p` is needed for container-to-container talk.** Inside, container doors are
  reached directly.

### Where it is used

- Task F7.9: the app reaches Postgres as `db:5432` over a compose-made network.
- Debugging reachability: `exec` into container A, try container B's IP.
- `docker inspect` (Lesson 16) reports this same IP under `NetworkSettings`.

### Link to the task

Subtask 8, first half — done as written: `docker network ls` showed where plain `docker run`
containers land; `docker network inspect bridge` listed the container with its own IP.
`docker inspect` completes the subtask in Lesson 16.

### Do it yourself

```bash
docker network ls                       # expect: bridge, host, none
docker network inspect bridge           # find pg17 under "Containers", note its "IPv4Address"

docker run -d --name buddy alpine sleep 120
docker exec buddy ping -c 2 <pg17-IP>   # expect: 2 received, 0% loss
docker rm -f buddy

ping -c 1 -t 2 <pg17-IP>                # from the Mac — expect: 100% packet loss
```

**Done when:** the IP was read out of `network inspect bridge`, a neighbour container reached
it, and the Mac could not.

### Summary for revision

- Plain `docker run` → the `bridge` network. One private IP per container, by start order.
- Inside: direct talk, container doors, no `-p`. Passwords still apply (network ≠ local).
- Outside (Mac): the street is invisible; only `-p` doors cross the wall.
- `localhost` in a container = that container itself.
- Stable names instead of shuffling IPs: custom networks, via compose (F7.9).

### Check questions, with answers

**Question 1.** A Postgres container `db` runs with `-p 5433:5432`. Inside, Postgres listens
on 5432. `docker network inspect bridge` shows `db -> 172.17.0.2`. Give the address and port
for each case:
(a) `pgcli` on the Mac connects to it.
(b) Another container on the bridge network connects to it.
(c) A program inside `db` itself connects to it.

**Answer.**
(a) `localhost:5433`. Mac programs knock on Mac doors; 5433 is the host side of the mapping.
(b) `172.17.0.2:5432`. Inside the network: direct to the container's IP and its real door.
Mappings do not exist there.
(c) `localhost:5432`. Inside the container, `localhost` is the container itself, where
Postgres listens on 5432.

**Question 2.** A Spring Boot app is containerized and started on the bridge network. Its
config says `spring.datasource.url=jdbc:postgresql://localhost:5432/shop`. A Postgres
container also runs on the bridge, mapped `-p 5432:5432`; `pgcli` from the Mac reaches it
fine. The app cannot connect. Why — and what worked before, and one fix?

**Answer.** Inside the app's container, `localhost` means the app's own container, where no
Postgres lives — the connection dies at its own door. The config worked when the app ran
outside Docker because `localhost` then meant the Mac, where the `-p 5432` door exists. Fixes:
point the URL at the Postgres container's bridge IP (fragile — IPs shuffle), or properly: put
both containers on a shared custom network and use the database container's name in the URL —
the compose way, Task F7.9.

### Your doubts

None in this lesson.

---

## Lesson 16 — `docker inspect`: the container's full paperwork

### Definition

`docker inspect <name>` prints **everything Docker knows about a container** as one JSON
document (~250 lines): state, configuration, storage, network. The task's tool for "a
container is behaving strangely".

### Why it is needed

Every fact hunted one at a time so far lives here together: running or not, exit code,
started when, which IP, which volumes, which environment variables. One command instead of
five. Task F7.9 reads container health from this same document.

### How it works, step by step

1. The output is JSON — nested name/value pairs, as in Task F4.6.
2. Four sections carry most of the value:
   - `.State` — running or exited, exit code, started when.
   - `.Config.Env` — every environment variable the container started with.
   - `.Mounts` — every volume and bind mount.
   - `.NetworkSettings.Networks` — networks and IP addresses.
3. `--format '{{...}}'` (short `-f`) extracts one field. Inside `{{ }}` is a path into the
   JSON, dots stepping one level down: `{{.State.Status}}`.
4. The same family covers every object: `docker volume inspect`, `docker network inspect`.

### Example — real commands, real output from this machine

**Treasure 1 — the IP.** The obvious path fails on today's Docker:

```text
$ docker inspect -f '{{.NetworkSettings.IPAddress}}' pg17
template parsing error: ... map has no entry for key "IPAddress"
```

| Error | Plain meaning |
|---|---|
| `map has no entry for key "IPAddress"` | The IP no longer sits at the top of `NetworkSettings`; it lives inside each network's entry. |

The correct path goes through the network's name:

```text
$ docker inspect -f '{{.NetworkSettings.Networks.bridge.IPAddress}}' pg17
172.17.0.3
```

**Treasure 2 — the volume, under `Mounts`:**

```text
$ docker inspect -f '{{json .Mounts}}' pg17
[{"Type":"volume","Name":"pgdata","Source":"/var/lib/docker/volumes/pgdata/_data",
  "Destination":"/var/lib/postgresql/data","RW":true,...}]
```

**Treasure 3 — the password, in plain sight, under `Env`:**

```text
$ docker inspect -f '{{json .Config.Env}}' pg17
["POSTGRES_PASSWORD=dev", "PATH=...", "PG_MAJOR=17", "PG_VERSION=17.11",
 "PGDATA=/var/lib/postgresql/data", ...]
```

Readable by anyone who can run `docker inspect`. The image's own baked-in variables ride
along — including `PGDATA`, how the start-up script knows the data folder path.

**The pulse — `.State`:**

```text
$ docker inspect -f '{{json .State}}' pg17
{"Status":"running","Running":true,"Pid":28132,"ExitCode":0,
 "StartedAt":"2026-11-18T07:58:12Z","OOMKilled":false,...}
```

`{{.State.Health}}` prints `<nil>` here — no health check defined; F7.9 writes there.

**The gotcha — a stopped container has no IP:**

```text
$ docker stop pg17 && docker inspect -f '...' pg17
IP when stopped: [invalid IP]   Status: exited   ExitCode: 0
$ docker start pg17             # IP returns: 172.17.0.3
```

Env, mounts and exit code stay readable while stopped; the address does not exist.

### Important terms

| Term | Meaning |
|---|---|
| `docker inspect <name>` | Full JSON paperwork of a container. |
| `--format` / `-f '{{...}}'` | Extract one field; dots walk down the JSON. |
| `.State` | Running/exited, exit code, start time, health. |
| `.Config.Env` | Environment variables — visible in plain text. |
| `.Mounts` | Volumes and bind mounts. |
| `.NetworkSettings.Networks` | Per-network entries holding the IPs. |

### Common mistakes

- **Drowning in the full JSON.** Daily use is `-f` with a path to one of the four sections.
- **Reading an empty IP as a bug.** Stopped containers have no IP; check `.State.Status` first.
- **Believing environment variables are private.** `docker inspect` and `docker exec <n> env`
  both show them to anyone with Docker access.
- **Using the old path `{{.NetworkSettings.IPAddress}}`.** Errors on Docker 29; go through
  `.Networks.bridge`.

### Where it is used

- The strange-behavior routine: check `State`, `Env`, `Mounts`, `Networks` in one go.
- Task F7.9: reading health from `.State.Health`.
- Scripts and CI: `--format` pulls one value for automation.

### Link to the task

Subtask 8 complete: all three treasures found in `docker inspect pg17` — IP under
`NetworkSettings`, volume under `Mounts`, `-e` password under `Env`, with the security moral:
environment variables are configuration, not secret storage; real secrets never travel as
plain environment variables. Checkpoint question 5 answered: `docker inspect <name>`.

### Do it yourself

```bash
docker inspect pg17                                                       # the full paperwork
docker inspect -f '{{.NetworkSettings.Networks.bridge.IPAddress}}' pg17   # expect: 172.17.0.x
docker inspect -f '{{json .Mounts}}' pg17                                 # pgdata -> /var/lib/postgresql/data
docker inspect -f '{{json .Config.Env}}' pg17                             # POSTGRES_PASSWORD=dev, plain text
docker inspect -f '{{json .State}}' pg17                                  # "Status":"running"

docker stop pg17
docker inspect -f '{{.State.Status}}' pg17        # exited — and the IP is gone
docker start pg17
```

**Done when:** all three treasures found, and checkpoint question 5 answers itself.

### Summary for revision

- `docker inspect <name>` = full JSON paperwork; `-f '{{path}}'` extracts one field.
- IP: `.NetworkSettings.Networks.bridge.IPAddress` (not the old top-level path).
- Volumes: `.Mounts`. Environment: `.Config.Env` — plain text, visible to all Docker users.
- State: `.State` (status, exit code, health). Stopped container → no IP.
- Same family: `docker volume inspect`, `docker network inspect`.

### Check questions, with answers

**Question 1.** A teammate's container `redis1` is acting strangely. Name the single command
that shows its IP address, its mounts, and its environment variables — and say which JSON
section each of the three lives in.

**Answer.** `docker inspect redis1`. The IP is under `NetworkSettings` (full path
`.NetworkSettings.Networks.bridge.IPAddress`), the mounts under `Mounts`, the environment
variables under `Config.Env`.

**Question 2.** A container was started with `-e API_KEY=supersecret123` on a shared build
server where several teammates can run Docker commands.
(a) Name two commands any of them could use to read that key.
(b) What is the practical rule this teaches about real secrets?

**Answer.** (a) `docker inspect <name>` (the key sits in `.Config.Env`) and
`docker exec <name> env` (prints the live environment inside). (b) Anything passed with `-e`
is readable by every person who can run Docker commands on that machine. A `dev` password on
a private laptop is fine; real secrets must never travel as plain environment variables —
they belong in proper secret stores, which later phases cover.

### Your doubts

None in this lesson.

---

## Lesson 17 — `docker stats`: what your containers really cost

### Definition

`docker stats` is a live meter showing, for every **running** container, its CPU, memory,
network and disk use right now. Activity Monitor for containers.

### Why it is needed

Containers feel weightless, but each running one eats real memory from one shared budget —
here, the hidden Linux VM's 2 GiB. The task's habit to build: know what your containers cost,
the way you know which apps slow your laptop.

### How it works, step by step

1. Plain `docker stats` **streams**: numbers repaint every second until `Ctrl-C` — which only
   stops the watching, never the containers (same as `logs -f`).
2. `docker stats --no-stream` takes one snapshot and exits — the form for notes and scripts.
3. Columns: `CPU %` (processor share right now), `MEM USAGE / LIMIT` (memory now, against the
   most it could get), `MEM %` (usage ÷ limit), `NET I/O` (network bytes in/out since start),
   `BLOCK I/O` (disk bytes read/written since start), `PIDS` (processes inside).
4. With no per-container limit set, `LIMIT` shows the **whole VM pot** — the same number on
   every row, shared, not per-container.
5. Only running containers appear; stopped ones cost nothing and are absent.

### Example — real commands, real output from this machine

Three tenants: the two Postgres containers plus a redis started for company.

```text
$ docker run -d --name cache redis:7-alpine
$ docker stats --no-stream
CONTAINER ID   NAME    CPU %   MEM USAGE / LIMIT     MEM %   NET I/O          BLOCK I/O         PIDS
3f92387a8377   cache   0.88%   4.621MiB / 1.913GiB   0.24%   516B / 126B      1.3MB / 0B        6
5ccd3d4564f1   pg17    0.03%   18.59MiB / 1.913GiB   0.95%   922B / 126B      0B / 422kB        6
207a49a2a0aa   pg      0.00%   46.41MiB / 1.913GiB   2.37%   3.33kB / 1.4kB   2.89MB / 89.8MB   6
```

Reading it:

- `LIMIT` is the same 1.913 GiB on every row — the VM's 2 GiB minus overhead, one shared pot.
- **Most expensive: `pg`, 46.41 MiB.** Same image as `pg17`, yet 2.5× the memory — it holds
  the real zenith databases and has served real queries, so its caches grew. Cost follows
  work done, not image size.
- Redis idles under 5 MiB.
- Total ≈ 70 MiB of 1958 MiB ≈ 3.6 % — nowhere near over budget.

Recorded in `NOTES.md` in this task folder: budget, per-container table, winner (`pg`).

### Important terms

| Term | Meaning |
|---|---|
| `docker stats` | Live streaming meter; `Ctrl-C` leaves it, containers unharmed. |
| `--no-stream` | One snapshot, then exit. |
| `MEM USAGE / LIMIT` | Memory now ÷ the most it could get (the shared VM pot if no limit set). |
| `BLOCK I/O` | Disk bytes read/written since the container started. |
| `PIDS` | Processes running inside. |

### Common mistakes

- **Judging by feel instead of measuring.** "Docker slows my laptop" is a guess; the snapshot
  is an answer.
- **Reading `LIMIT` as a per-container guarantee.** Three rows showing 1.913 GiB share one
  pot; they do not have ~6 GiB between them.
- **Looking for a stopped container in the list.** Absence is normal: stopped costs nothing.
- **Comparing `MEM %` across machines.** 2 % of 2 GiB and 2 % of 16 GiB differ hugely;
  compare MiB, not percent.

### Where it is used

- Before adding a service: is there room for one more container?
- When the laptop crawls: which container is eating what?
- Production monitoring dashboards answer the same question, grown up.
- Compose (F7.9) can set real per-container limits; `LIMIT` then shows the cap.

### Link to the task

Subtask 9 complete: `docker stats` ran with several containers; each container's memory is
recorded in `NOTES.md` against the 2 GiB budget; most expensive container named: `pg`
(46.41 MiB), everything far under budget.

### Do it yourself

```bash
docker run -d --name cache redis:7-alpine   # company for the meter
docker stats --no-stream                    # one row per running container; same LIMIT everywhere
docker stats                                # watch live, then Ctrl-C — containers keep running

# Record in NOTES.md (in the F7.8 task folder): the LIMIT value (your VM budget),
# one line per container (name, MEM USAGE, MEM %), the total, the most expensive name.

docker rm -f cache                          # tidy up
```

**Done when:** `NOTES.md` names your most expensive container and says whether anything is
near the budget.

### Summary for revision

- `docker stats` streams; `--no-stream` snapshots; `Ctrl-C` never touches containers.
- `LIMIT` with no caps = the whole shared VM pot, same on every row.
- Only running containers appear.
- Cost follows work done: same image, different memory.
- Measure MiB, write it down, name the most expensive tenant.

### Check questions, with answers

**Question 1.** On a laptop whose Docker VM has 2 GiB of memory, `docker stats --no-stream`
shows:

```text
NAME   MEM USAGE / LIMIT     MEM %
api    310MiB / 1.913GiB     15.8%
db     160MiB / 1.913GiB     8.2%
```

(a) Why do both rows show the same `LIMIT`?
(b) Roughly how much of the VM's memory is actually in use, and is this machine in trouble?

**Answer.** (a) Neither container has its own memory cap, so Docker shows the most either
could get: the whole VM pot (2 GiB minus overhead = 1.913 GiB). It is one shared pot, not a
per-container allowance. (b) About 470 MiB of ~1958 MiB — roughly a quarter. No trouble:
three-quarters of the budget is free; `api` is the most expensive tenant and the one to watch.

**Question 2.** A teammate runs `docker stats`, sees it "keeps flickering and never exits",
presses `Ctrl-C`, and then worries they just killed their database container. What was
actually happening, what did `Ctrl-C` do — and which variant gives a single clean snapshot?

**Answer.** Plain `docker stats` is a live meter: it repaints every second on purpose and
never exits on its own. `Ctrl-C` closed only the meter — the watching — exactly like leaving
`docker logs -f`; no container was touched, and `docker ps` would prove the database still
up. For one clean snapshot: `docker stats --no-stream`.

### Your doubts

None in this lesson.

---

## Lesson 18 — Checkpoint: five questions and the final proof

### Definition

The task's closing gate: five questions to answer out loud, and a **done-when** test the
machine itself must pass. No new concepts — this is where the previous seventeen lessons
prove they stuck.

### Why it is needed

Reading feels like knowing. Answering out loud, with nothing to peek at, finds the gaps —
and the done-when turns "I think I understand volumes" into a container destroyed on purpose
and the data walking out unharmed.

### The five questions — try them out loud first, notes closed

1. What is the difference between an image and a container?
2. In `-p 5433:5432`, which number is your machine and which is the container?
3. You deleted a container and lost your tables. What should you have used?
4. How do you get a shell inside a running container, and why would you want one?
5. A container is misbehaving. Which single command shows you its IP address, its mounts,
   and the environment variables it was started with?

### The answers

**1 — Image vs container.** An image is a read-only template: a frozen package of a program
and everything it needs. A container is a running copy made from that template, with its own
thin writable layer on top. One image can back many containers at once; deleting a container
never touches the image.

**2 — Which side is which in `-p 5433:5432`.** Host first, container second. `5433` is
**your machine's** door; `5432` is the **container's** door, where Postgres actually listens.
Mac tools knock on `localhost:5433`; Docker carries the knock through the wall to 5432 inside.

**3 — Deleted container, lost tables.** A **named volume**:
`-v pgdata:/var/lib/postgresql/data`. Without it, tables live in the container's writable
layer, and `docker rm` destroys that layer. With it, the data folder lives in the volume,
which `docker rm` does not touch — a fresh container started with the same `-v` adopts the
data, tables intact.

**4 — A shell inside, and why.** `docker exec -it <name> bash` — exec starts an *extra*
command inside the running container, `-it` makes it interactive, `bash` is the shell. You
want one to see the container's world from within: run `psql` directly, read config files,
check the data folder — debugging where the problem actually lives.

**5 — The misbehaving container.** `docker inspect <name>`. One JSON document: the IP under
`NetworkSettings`, the volumes under `Mounts`, the environment variables — password included,
in plain text — under `Config.Env`.

### The done-when proof — real commands, real output from this machine

The task's bar: *"Postgres runs on a non-default host port with a named volume, its data
survives `docker rm` and a fresh start, and you have opened a shell inside it."* All three,
witnessed on 2026-11-18:

**Proof 1 — non-default port, named volume.** `docker ps` before the test:

```text
CONTAINER ID   IMAGE                STATUS         PORTS                     NAMES
5ccd3d4564f1   postgres:17-alpine   Up 4 minutes   0.0.0.0:5433->5432/tcp    pg17
```

Host port **5433**, not the default 5432 — and the container was started with
`-v pgdata:/var/lib/postgresql/data`.

**Proof 2 — data survives destruction.** The `visitors` table held 2 rows. Then:

```text
$ docker rm -f pg17                                   # container 5ccd3d4564f1 destroyed
$ docker run -d --name pg17 -e POSTGRES_PASSWORD=dev \
    -p 5433:5432 -v pgdata:/var/lib/postgresql/data postgres:17-alpine
e0c807bca80e...                                       # a brand-new container

$ docker exec pg17 psql -U postgres -c "SELECT * FROM visitors;"
 id |  name
----+--------
  1 | first
  2 | second
(2 rows)
```

New container (ID `e0c8...`, not `5ccd...`), same two rows — carried by the `pgdata` volume.

**Proof 3 — a shell inside.**

```text
$ docker exec pg17 bash -c 'echo "a shell ran inside container $(hostname)"'
a shell ran inside container e0c807bca80e
```

The hostname is the new container's own ID — the shell really ran inside. (The interactive
form, `docker exec -it pg17 bash`, is the same door with a prompt.)

### Common mistakes

- **Checking answers by rereading instead of recalling.** Gaps only show when the notes are
  closed.
- **Skipping the done-when because "it worked earlier".** The proof takes thirty seconds and
  catches silent drift (wrong volume name, wrong port) that memory never will.

### Where it is used

This checkpoint *is* the daily Docker toolkit for the rest of the phases:
`run -d --name -e -p -v`, `ps`, `logs -f`, `exec -it`, `stop`, `rm`, `inspect`, `stats`.
Task F7.9 stacks compose on top of exactly these.

### Link to the task

The task's final section. All ten subtasks done, all five checkpoint questions answered, all
three done-when conditions proven live.

### Do it yourself

```bash
# 1. Your container should be up on 5433 with the pgdata volume. Verify:
docker ps                                    # expect: 0.0.0.0:5433->5432/tcp, name pg17

# 2. Give it something to lose — then destroy it:
docker exec pg17 psql -U postgres -c "SELECT * FROM visitors;"   # expect: your rows
docker rm -f pg17

# 3. Fresh start on the same volume:
docker run -d --name pg17 -e POSTGRES_PASSWORD=dev -p 5433:5432 \
  -v pgdata:/var/lib/postgresql/data postgres:17-alpine
sleep 3

# 4. The data walked out unharmed:
docker exec pg17 psql -U postgres -c "SELECT * FROM visitors;"   # expect: same rows

# 5. A shell inside:
docker exec -it pg17 bash     # prompt appears; hostname is the container ID; exit to leave

# 6. The five questions, out loud, notes closed.
```

**Done when:** same rows after the rm-and-fresh-start, a shell prompt appeared inside, and
all five questions answered without peeking.

### Summary for revision

- The five answers in five lines: image = template, container = running copy · `-p` is
  host:container, host first · lost tables → you needed a named volume · shell inside =
  `docker exec -it <name> bash` · misbehaving container → `docker inspect <name>`.
- The done-when ritual: `ps` (port + volume) → `rm -f` → `run` with the same `-v` → rows
  survive → `exec -it` shell.
- The one command this task builds to:
  `docker run -d --name pg17 -e POSTGRES_PASSWORD=dev -p 5433:5432 -v pgdata:/var/lib/postgresql/data postgres:17-alpine`

### Check questions, with answers

**Question 1.** A teammate claims their setup matches this task's done-when. Their container
runs with `-p 5432:5432` and no `-v` flag. Which of the three done-when conditions do they
fail, and what happens to their tables on `docker rm`?

**Answer.** They fail two: the host port is the default 5432 (not a non-default one like
5433), and there is no named volume. Only the shell condition can still pass. On `docker rm`,
their tables are destroyed with the container's writable layer — no volume holds them — and a
fresh container starts completely empty.

**Question 2.** Write, from memory, the single `docker run` command this whole task builds
to: detached, named `pg17`, password `dev`, host port 5433 to container port 5432, named
volume `pgdata` on Postgres's data folder, image `postgres:17-alpine`.

**Answer.**

```bash
docker run -d --name pg17 -e POSTGRES_PASSWORD=dev -p 5433:5432 \
  -v pgdata:/var/lib/postgresql/data postgres:17-alpine
```

Flag by flag: `-d` background · `--name pg17` the handle · `-e POSTGRES_PASSWORD=dev` the
first-start password · `-p 5433:5432` Mac door 5433 → container door 5432 · `-v
pgdata:/var/lib/postgresql/data` the named volume holding the data folder · the image last.

### Your doubts

None in this lesson.

---

## Task F7.8 — closed

Completed 2026-11-18. Eighteen lessons, all ten subtasks, the five checkpoint questions, and
the done-when proven live (data survived `docker rm` + fresh start on the `pgdata` volume).

Companion files in this folder:

- `NOTES.md` — the subtask-9 memory measurements (budget 2 GiB, most expensive: `pg`).
- `backups/zenith-2026-11-18.sql`, `backups/zenith_lab-2026-11-18.sql` — safety dumps of the
  F5/F6 databases taken during Lesson 12. Keep them.
- `seed/001-init.sql` and `bind-demo/` — the Lesson 14 bind-mount demos.

Machine end-state: `pg17` up on 5433 (volume `pgdata`) · `pg` up on 5432 with the zenith
databases (volume `pgdata-f5`). Next task: F7.9 — docker compose, which wires containers like
these together with named networks, health checks, and one `up` command.

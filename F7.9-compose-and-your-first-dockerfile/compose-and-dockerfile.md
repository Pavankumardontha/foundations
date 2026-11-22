# F7.9 — Compose, and your first Dockerfile

Notes for Task F7.9. One lesson per section. Each lesson ends with its understanding-check
questions and full answers. All command output in these notes is real output, captured on
this machine.

---

## Lesson 1 — Why Compose exists

### Definition

**Compose** is a tool built into Docker. It reads one file, usually named `compose.yml`,
that describes several containers. It then starts, lists, and stops all of them with single
commands.

Two reminders so this lesson stands on its own. An **image** is a frozen, ready-to-run copy
of a program and everything it needs. A **container** is one running instance made from an
image, in its own isolated box.

### Why it is needed

Without Compose, you start each container by hand with `docker run`. Real command, real
output:

```text
$ docker run -d --name demo-redis -p 6379:6379 redis:7-alpine
05381005e77c0f5f240ef4929ea37b588b01c80d9226006b836aa16a4478825b
```

That is one container, and already five flags and values to remember. A real project also
needs Postgres, with a password, a database name, a port, and a volume. That is a second,
longer command. Every morning, all of them, in the right order. Three problems follow:

- You must remember every flag. Forget one and things half-work.
- A teammate types slightly different flags. The two machines now behave differently.
- The commands live only in your shell history. A new teammate has nothing to copy from.

Compose fixes all three. The flags move into one file. The file lives in the project folder
and goes into git. Starting everything becomes one short command, the same for everyone.

### How it works, step by step

1. You write a file named `compose.yml` in your project folder.
2. In it you list **services**. A service is one container entry with a name you choose.
3. Under each service you put the same settings you used to pass as `docker run` flags.
4. You run `docker compose up -d` in that folder. `-d` means: run in the background and
   give my prompt back.
5. Compose reads the file and asks Docker to create one container per service. It also
   creates a private network for them (that network gets its own lesson).
6. `docker compose down` stops and removes everything the file created.

Naming note: the modern file name is `compose.yml` (or `compose.yaml`). The older name
`docker-compose.yml` still works.

### Example — real run on this machine

The smallest useful `compose.yml`, in a folder named `hello-compose`:

```yaml
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

In plain words: "I want one container. I call it `redis`. Build it from the image
`redis:7-alpine`. Connect port 6379 on my machine to port 6379 in the container."

```text
$ docker compose up -d
 Network hello-compose_default  Created
 Container hello-compose-redis-1  Created
 Container hello-compose-redis-1  Started

$ docker compose ps
NAME                    IMAGE            SERVICE   STATUS                  PORTS
hello-compose-redis-1   redis:7-alpine   redis     Up Less than a second   0.0.0.0:6379->6379/tcp

$ docker compose down
 Container hello-compose-redis-1  Removed
 Network hello-compose_default  Removed
```

Reading the output:

1. `Network hello-compose_default Created` — before touching any container, Compose created
   a private network, named after the folder plus `_default`. The file never mentioned it.
2. `Container hello-compose-redis-1 Created / Started` — one container for the one service.
   The name has three parts: `hello-compose` (the folder, which Compose calls the
   **project**), `redis` (the service name from the file), `1` (first copy of that service).
3. `docker compose ps` lists only this project's containers. `SERVICE` shows the name from
   the file.
4. `docker compose down` undid everything in reverse: stop container, remove container,
   remove network. Nothing keeps running afterwards.

Key mental picture: Compose did not replace Docker. It read the file and asked Docker to do
exactly what your `docker run` flags used to ask. The containers are ordinary containers,
and plain `docker ps` shows them too.

### Important terms

- **Compose** — the Docker tool that runs several containers from one file.
- **compose.yml** — the file that describes the containers you want.
- **Service** — one container entry in `compose.yml`, with a name you choose.
- **Project** — Compose's name for one compose file and everything it creates. By default
  the project name is the folder name.
- **`docker compose up -d`** — create and start everything in the file, in the background.
- **`docker compose ps`** — list this project's containers and their status.
- **`docker compose down`** — stop and remove everything the file created.

### Common mistakes

1. **Running the command in the wrong folder.** Compose looks for `compose.yml` in the
   folder you are standing in. Real output from a folder without one:

   ```text
   $ docker compose ps
   no configuration file provided: not found
   ```

   Decoded: "configuration file" means `compose.yml`; "not found" means it is not in this
   folder. Fix: `cd` into the folder that holds the file.
2. **Thinking Compose is a separate engine.** It is a planner that drives Docker. If an
   image is broken under `docker run`, Compose cannot fix it.
3. **Thinking closing the terminal stops the containers.** With `up -d` they keep running
   in the background until `docker compose down`.

### Where it is used

- Local development on almost every backend team: one `docker compose up -d` brings up the
  database, the cache, and the message broker.
- Onboarding: a new teammate clones the repo and runs one command.
- CI pipelines: tests start their databases the same way.

### Link to the task

The first subtask says: "Write a `compose.yml` with Postgres and Redis, a named volume for
Postgres, and a health check." This lesson is the idea that file is built on: one service
per container, one file for the whole stack, one command to start it.

### Summary for revision

- Compose turns many `docker run` commands into one file plus one command.
- One service in the file = one container when running.
- Container names follow the pattern `project-service-number`.
- `up -d` starts, `ps` checks, `down` removes. `down` also removes the network.
- The command must run in the folder that holds `compose.yml`.

### Check questions, with answers

**Question 1.** Every morning, a teammate starts work by typing these two commands:

```bash
docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 postgres:17-alpine
docker run -d --name cache -p 6379:6379 redis:7-alpine
```

What would Compose replace these two commands with? Name two concrete things that get
better after the switch.

**Answer.** Compose replaces them with one `compose.yml` holding two services, plus the
single command `docker compose up -d`. The file (its exact keys are taught in Lessons 2
and 3):

```yaml
services:
  pg:
    image: postgres:17-alpine
    environment:
      POSTGRES_PASSWORD: dev
    ports:
      - "5432:5432"
  cache:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

What gets better (any two): the flags now live in a file that goes into git, so every
machine runs the same settings; one short command replaces several long ones; a new
teammate needs no wiki page of commands; `docker compose down` cleans everything up with
one command.

**Question 2.** A folder named `notes` contains no `compose.yml`, and no folder above it
contains one either. Standing inside `notes`, you run `docker compose up -d`. What
happens, and why?

**Answer.** No container starts. Compose prints:

```text
no configuration file provided: not found
```

Compose only acts on a `compose.yml` (or `compose.yaml`, or the older `docker-compose.yml`)
that it finds in the current folder or a folder above it. There is none, so it stops with
this error. The fix is to `cd` into the folder that holds the file.

### Do it yourself (on a fresh laptop)

1. Check Compose is available:

   ```bash
   docker compose version
   ```

   Success: `Docker Compose version v2.x` (or `5.x`). Failure: `docker: unknown command:
   docker compose`.
2. If it failed: on Docker Desktop, update Docker Desktop — Compose is bundled. With
   Colima and a Homebrew docker CLI, run:

   ```bash
   brew install docker-compose
   mkdir -p ~/.docker/cli-plugins
   ln -sf /opt/homebrew/bin/docker-compose ~/.docker/cli-plugins/docker-compose
   docker compose version
   ```

3. Make a folder `hello-compose`. Inside it create `compose.yml` with exactly:

   ```yaml
   services:
     redis:
       image: redis:7-alpine
       ports:
         - "6379:6379"
   ```

4. From inside that folder run, in order: `docker compose up -d`, then
   `docker compose ps`, then `docker compose down`. Expected: `up` prints `Created` and
   `Started` lines, `ps` shows one container with STATUS `Up`, `down` prints `Removed`
   lines.
5. Also run `docker compose ps` one folder up, where there is no `compose.yml`. Expected
   failure: `no configuration file provided: not found`.
6. **Done when:** the up–ps–down loop works and you can explain the three parts of the
   container name `hello-compose-redis-1`.

### Your doubts

None yet.

---

## Lesson 2 — Reading `compose.yml`: services

### Definition

`compose.yml` is written in a text format called **YAML**. In YAML, structure comes from
indentation: lines indented under a thing belong to that thing. The top section of every
compose file is `services:`, the list of containers you want.

### Why it is needed

Compose must know exactly which containers you want and which settings belong to which
container. A `docker run` command shows belonging by putting flags on one line. A file
cannot do that, so YAML uses indentation to say "this setting belongs to that service".
Wrong indentation means Compose refuses the file.

### How it works, step by step

Four YAML rules cover everything this task needs:

1. **`key: value`** — one setting per line, with a required space after the colon.
   Example: `image: redis:7-alpine`.
2. **Indent two spaces to say "belongs to".** A line indented under `postgres:` is a
   setting of the `postgres` service.
3. **A line starting with `- `** is one item in a list. Ports are written this way.
4. **Spaces only. Never tabs.** YAML rejects tab characters outright.

Two Compose rules on top:

5. Directly under `services:` come the **service names**. You choose them: short,
   lowercase names like `postgres` or `redis`.
6. Under each service name come its settings. The one setting every service needs is
   `image:` — which image to make the container from.

### Example — real run on this machine

The start of the task file, in a folder named `zenith-stack`:

```yaml
services:
  postgres:
    image: postgres:17-alpine
  redis:
    image: redis:7-alpine
```

Line by line: `services:` is not indented, so it is a top-level section. `postgres:` is
indented two spaces, so it belongs to `services` — it is a service name I chose (the image
happens to share the name; the service name is purely my label). `image: postgres:17-alpine`
is indented two more, so it is a setting of the `postgres` service. `redis:` steps back to
two spaces, so it is a second service, a sibling of `postgres`.

`docker compose config` checks a compose file without starting anything. It prints the file
back the way Compose understood it, or an error:

```text
$ docker compose config
name: zenith-stack
services:
  postgres:
    image: postgres:17-alpine
    networks:
      default: null
  redis:
    image: redis:7-alpine
    networks:
      default: null
networks:
  default:
    name: zenith-stack_default
```

Worth noticing: Compose took the project name from the folder. And it added
`networks: default` to both services on its own — it plans one private network,
`zenith-stack_default`, for the whole project. The `null` means "no special settings".

### Important terms

- **YAML** — the text format compose files are written in. Indentation shows belonging.
- **Mapping** — YAML's word for a block of `key: value` lines.
- **Service name** — the label you choose for one container entry, directly under
  `services:`.
- **`docker compose config`** — parse and print the file as Compose understands it,
  without starting anything. The cheap "did I type it right?" check.

### Common mistakes

1. **Wrong indentation.** `image:` moved left, to the same depth as the service names:

   ```yaml
   services:
     postgres:
     image: postgres:17-alpine
   ```

   ```text
   $ docker compose config
   services.image must be a mapping
   ```

   Decoded: because of its position, Compose thinks `image` is a **service name**. A
   service must be a mapping (a block of settings), but this `image` has only a single
   text value. The error naming `image` as if it were a service is the clue that
   indentation slipped.
2. **A tab character instead of spaces.**

   ```text
   $ docker compose config
   yaml: line 3: found character that cannot start any token
   ```

   Decoded: YAML met a tab. It names the line. Set your editor to insert spaces.
3. **Trusting your eyes instead of the checker.** Indentation bugs are hard to see. Run
   `docker compose config` after every edit.

### Where it is used

The same YAML shape is everywhere in backend work: Spring's `application.yml`, GitHub
Actions pipelines, Kubernetes manifests. Indentation-as-belonging pays off far beyond
Compose.

### Link to the task

The first subtask's file now exists as a parsing skeleton: two services, correct images,
correct shape. Lesson 3 adds ports, the password, the database name, and the named volume.
Lesson 4 adds the healthcheck.

### Summary for revision

- YAML: `key: value`, two-space indents show belonging, `- ` starts a list item, never
  tabs.
- `services:` on top; under it the service names you choose; under each, its settings.
- Every service needs `image:`.
- `docker compose config` parses and prints the file, or the exact error.

### Check questions, with answers

**Question 1.** A folder named `shop` contains this `compose.yml`:

```yaml
services:
  db:
    image: postgres:17-alpine
  cache:
    image: redis:7-alpine
  queue:
    image: rabbitmq:3-alpine
```

You run `docker compose up -d` inside `shop`. How many containers are created, and what is
the full name of each one?

**Answer.** Three containers, one per service: `shop-db-1`, `shop-cache-1`, and
`shop-queue-1`. The pattern is project name (the folder, `shop`), then the service name
from the file, then the copy number, joined with dashes. Compose also creates one network,
`shop_default`, but that is a network, not a container.

**Question 2.** A teammate writes this file, and `docker compose config` answers
`services.image must be a mapping`:

```yaml
services:
  postgres:
  image: postgres:17-alpine
```

Explain what Compose thought `image` was, and show the corrected file.

**Answer.** `image` sits at the same indentation depth as `postgres`, so Compose read it
as a second service name. A service must be a mapping — a block of settings — but this
`image` carries only the text `postgres:17-alpine`, hence "must be a mapping". The fix is
to indent `image:` two spaces further, so it belongs to `postgres`:

```yaml
services:
  postgres:
    image: postgres:17-alpine
```

### Do it yourself (on a fresh laptop)

1. Make a folder `zenith-stack`. Inside it create `compose.yml` with exactly:

   ```yaml
   services:
     postgres:
       image: postgres:17-alpine
     redis:
       image: redis:7-alpine
   ```

2. Run `docker compose config` inside that folder. Expected: it prints the file back with
   a `name:`, your two services, and an added `networks:` block.
3. Break it: remove two spaces before one `image:` line so it lines up with the service
   names. Run `docker compose config`. Expected failure: `services.image must be a
   mapping`. Put the spaces back.
4. Break it differently: replace the spaces before one `image:` with a single tab.
   Expected failure: `yaml: line N: found character that cannot start any token`. Fix it.
5. **Done when:** the file parses cleanly and you have seen and fixed both errors
   yourself.

### Your doubts

None yet.

---

## Lesson 3 — Ports, environment, and named volumes in the file

### Definition

Three settings inside a service entry, each replacing one `docker run` flag:

- `ports:` replaces `-p` — connect a door on your Mac to a door in the container.
- `environment:` replaces `-e` — named text settings the program reads when it starts.
- `volumes:` replaces `-v` — named storage that Docker manages and that outlives the
  container.

### Why it is needed

Each one prevents a real failure:

- Without `ports:`, nothing on your Mac can reach the container.
- Without `environment:`, the Postgres image refuses to start (real refusal below).
- Without `volumes:`, the database files live inside the container, and every
  `docker compose down` (which removes containers) deletes all data.

### How it works, step by step

**Ports** — a list under the service, one item per mapping, always quoted:

```yaml
    ports:
      - "5432:5432"
```

Left of the colon: the door on your Mac. Right: the door inside the container. The quotes
keep the value one piece of text (it contains a colon, and some digit pairs get misread as
numbers when unquoted).

**Environment** — a block of `KEY: value` lines under the service:

```yaml
    environment:
      POSTGRES_PASSWORD: dev
      POSTGRES_DB: zenith
```

An environment variable is a named text setting a program reads at startup. The Postgres
image reads `POSTGRES_PASSWORD` (required) and `POSTGRES_DB` (on first start, create a
database with this name).

**Volumes** — two parts, both required. In the service, "attach volume to folder":

```yaml
    volumes:
      - pgdata:/var/lib/postgresql/data
```

Left: a volume name you choose. Right: the folder inside the container —
`/var/lib/postgresql/data` is where Postgres keeps every database file. Then at top level,
the declaration that the project owns this volume:

```yaml
volumes:
  pgdata:
```

### Example — real run on this machine

The complete file (subtask 1, minus only the healthcheck):

```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_PASSWORD: dev
      POSTGRES_DB: zenith
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

volumes:
  pgdata:
```

It went live in four stages, with three real failures on the way.

**Stage 1 — ports only, port already taken.** With only `ports:` added and an old
container still holding 5432:

```text
$ docker compose up -d
 Container zenith-stack-postgres-1 Starting
 Error response from daemon: ... Bind for 0.0.0.0:5432 failed: port is already allocated
```

Decoded: one door, one listener. Something already listens on 5432 on this machine. Find
it with `docker ps`, free it with `docker stop <name>`. Compose also printed
`redis-1 interrupted by the failure of postgres-1`: when one service fails to start,
Compose aborts the rest.

**Stage 2 — port free, but no password.** Same file, `up -d` again:

```text
$ docker compose ps -a
NAME                      SERVICE    STATUS
zenith-stack-postgres-1   postgres   Exited (1) Less than a second ago
zenith-stack-redis-1      redis      Up Less than a second
```

Plain `ps` shows only running containers; `-a` also shows stopped ones. Postgres started
and immediately died. The reason is in its output — `docker compose logs postgres` prints
what a service wrote:

```text
postgres-1  | Error: Database is uninitialized and superuser password is not specified.
postgres-1  |        You must specify POSTGRES_PASSWORD to a non-empty value for the
postgres-1  |        superuser.
```

The image refuses to create a brand-new database without a password. That is what the
`environment:` block is for.

**Stage 3 — the full file works.** With `environment:` and `volumes:` added:

```text
$ docker compose up -d
 Volume zenith-stack_pgdata  Created
 Container zenith-stack-postgres-1  Recreated
 Container zenith-stack-postgres-1  Started
```

The volume got the project prefix: `zenith-stack_pgdata`. `Recreated` means Compose
noticed the service's settings changed and replaced the container with a fresh one.
Proofs (`docker exec` runs a command inside a running container):

```text
$ docker compose ps
NAME                      SERVICE    STATUS                  PORTS
zenith-stack-postgres-1   postgres   Up Less than a second   0.0.0.0:5432->5432/tcp
zenith-stack-redis-1      redis      Up 17 seconds           0.0.0.0:6379->6379/tcp

$ docker exec zenith-stack-postgres-1 psql -U postgres -d zenith -c 'select now();'
              now
-------------------------------
 2026-11-17 17:52:38.594764+00

$ docker exec zenith-stack-redis-1 redis-cli ping
PONG
```

**Stage 4 — `down` does not touch the volume.**

```text
$ docker compose down
 ... containers Removed, network Removed ...

$ docker volume ls --filter name=zenith
local     zenith-stack_pgdata
```

Containers and network gone; the volume with the database files survives. The next `up`
reattaches it.

### Important terms

- **Port mapping** — "host:container": the door on your Mac connected to the door inside
  the container.
- **Environment variable** — a named text setting a program reads when it starts.
- **Named volume** — storage with a name, managed by Docker, attached to a folder inside
  the container. It survives container removal.
- **Top-level `volumes:` block** — declares which named volumes the project owns.
- **Project prefix** — Compose prefixes what it creates with the project name:
  `zenith-stack_pgdata`.
- **Recreated** — Compose replaced a container because its settings in the file changed.
- **`docker exec`** — run a command inside a running container.

### Common mistakes

1. **Using a named volume without declaring it.** Without the top-level block:

   ```text
   $ docker compose config
   service "postgres" refers to undefined volume pgdata: invalid compose project
   ```

   Fix: add the top-level `volumes:` block with `pgdata:` under it.
2. **Unquoted port values.** `- 5432:5432` usually works, until a value like `22:22` is
   read as a strange number instead of text. Always quote.
3. **Changing `POSTGRES_PASSWORD` or `POSTGRES_DB` on an existing volume.** These act
   only on first start, when the volume is empty. Once database files exist, the image
   ignores them. To truly start over, delete the volume (`down -v`, Lesson 5).

### Where it is used

Nearly every service you run locally has this shape: an image, a port so you can reach
it, environment variables to configure it, and a volume if it stores anything worth
keeping. Databases, caches, message brokers alike.

### Link to the task

Subtask 1 ("Postgres and Redis, a named volume for Postgres, and a health check") is now
written and proven, except the healthcheck. The day even supplied the reason healthchecks
exist: right after `up`, with `ps` showing `Up`, the very first `psql` failed with

```text
psql: error: connection to server on socket "/var/run/postgresql/.s.PGSQL.5432" failed
	Is the server running locally and accepting connections on that socket?
```

and the same command worked two seconds later. "Up" is not "ready".

### Summary for revision

- `ports: - "host:container"`, quoted. Left: your Mac's door. Right: the container's.
- `environment:` is `KEY: value` pairs the program reads at startup. Postgres needs
  `POSTGRES_PASSWORD`; `POSTGRES_DB` names the database created on first start.
- Named volume: attach with `- name:/path/inside` in the service, declare with a
  top-level `volumes:` block. Real name gets the project prefix.
- `down` removes containers and network but keeps volumes.
- Settings change + `up -d` = container `Recreated`.

### Check questions, with answers

**Question 1.** A folder `zenith-stack` holds this file:

```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_PASSWORD: dev
      POSTGRES_DB: zenith
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

On your Mac you run `psql -h localhost -p 5432 -U postgres` and it connects into this
container. Which line of the file makes that possible, and what do the number on the left
and the number on the right of its colon each mean?

**Answer.** The line `- "5432:5432"` under `ports:`. The left number is the door on your
Mac — the one `psql -h localhost -p 5432` dials. The right number is the door inside the
container, where Postgres actually listens. The mapping connects the two, so a connection
to your Mac's 5432 lands on the container's 5432.

**Question 2.** A teammate's file says:

```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_PASSWORD: dev
    volumes:
      - pgdata:/var/lib/postgresql/data
```

`docker compose config` answers: `service "postgres" refers to undefined volume pgdata:
invalid compose project`. What is missing, what exactly do they add, and — once it works,
run from a folder named `zenith-stack` — what full name will the created volume have?

**Answer.** The top-level `volumes:` block is missing. They add, at the bottom of the
file, not indented:

```yaml
volumes:
  pgdata:
```

The created volume is named `zenith-stack_pgdata`: project name (the folder), underscore,
volume name.

### Do it yourself (on a fresh laptop)

1. In your `zenith-stack` folder, write the file with **ports only** (no environment, no
   volumes) and run `docker compose up -d`. If something already holds 5432, expect
   `Bind for 0.0.0.0:5432 failed: port is already allocated` — find it with `docker ps`,
   stop it with `docker stop <name>`.
2. With the port free, `up -d` again, then `docker compose ps -a`: postgres shows
   `Exited (1)`. Read why with `docker compose logs postgres` — expect `Database is
   uninitialized and superuser password is not specified`.
3. Replace the file with the full version from this lesson. Run `docker compose up -d` —
   expect `Volume zenith-stack_pgdata Created` and postgres `Recreated`.
4. Prove it: `docker compose ps` shows both `Up`;
   `docker exec zenith-stack-postgres-1 psql -U postgres -d zenith -c 'select now();'`
   prints a timestamp (if it says `not accepting connections`, wait two seconds and
   retry); `docker exec zenith-stack-redis-1 redis-cli ping` prints `PONG`.
5. Run `docker compose down`, then `docker volume ls` — `zenith-stack_pgdata` must still
   be listed.
6. **Done when:** both services run, Postgres answers SQL in the `zenith` database, Redis
   answers PONG, and the volume survives a `down`.

### Your doubts

None yet.

---

## Lesson 4 — Healthchecks: "started" is not "ready"

### Definition

A **healthcheck** is a command Docker runs *inside* a container every few seconds. While
the command keeps passing, the container is labeled **healthy**. The label shows in
`docker compose ps`.

### Why it is needed

A container being "Up" says nothing about the program inside being ready. Real capture
from this machine: `docker compose ps` showed Postgres as `Up Less than a second`, and in
that same second:

```text
$ docker exec zenith-stack-postgres-1 psql -U postgres -d zenith -c 'select now();'
psql: error: connection to server on socket "/var/run/postgresql/.s.PGSQL.5432" failed
	Is the server running locally and accepting connections on that socket?
```

Two seconds later the same command worked. The container existed within milliseconds;
Postgres needed seconds. An app that starts "after" the database container can land in
that gap, get connection refused, and crash — a classic first-job bug. The healthcheck
gives a trustworthy, machine-readable "the program inside now actually answers".

### How it works, step by step

The block added to the `postgres` service:

```yaml
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 2s
      timeout: 3s
      retries: 15
```

- `test:` — the command to run inside the container. `CMD-SHELL` means "run this text
  through a shell in there". `pg_isready` is Postgres's own probe: success when the
  server accepts connections.
- `interval: 2s` — run the probe every 2 seconds.
- `timeout: 3s` — a probe hanging longer than 3 seconds counts as a failure.
- `retries: 15` — only after 15 failures in a row, label the container **unhealthy**.
  About 30 seconds of patience for a slow first boot.

Label journey: `(health: starting)` from launch (no verdict yet) → first passing probe
flips it to `(healthy)` → 15 straight failures would flip it to `(unhealthy)`.

The label *by itself changes nothing*. Nothing waits, nothing restarts. Something must
consume it — that is `depends_on` with `condition: service_healthy`, Lesson 7.

### Example — real run on this machine

Immediately after `docker compose up -d`:

```text
NAME                      SERVICE    STATUS
zenith-stack-postgres-1   postgres   Up Less than a second (health: starting)
zenith-stack-redis-1      redis      Up Less than a second
```

Redis shows no health note at all: no healthcheck defined, no verdict, ever. Two seconds
later:

```text
zenith-stack-postgres-1   postgres   Up 2 seconds (healthy)
```

Then the probe was broken on purpose — `test: ["CMD-SHELL", "false"]` (a command that
always fails) with `retries: 2`:

```text
zenith-stack-postgres-1   postgres   Up 4 seconds (unhealthy)
```

And the punchline, run while the container wore that label:

```text
$ docker exec zenith-stack-postgres-1 psql -U postgres -d zenith -c 'select 1;'
 ?column?
----------
        1
```

The database answered SQL while labeled unhealthy. The label is the *probe's verdict*,
not the program's actual state. Good probe, trustworthy label. Broken probe, false alarm.

### Important terms

- **Healthcheck** — a probe command Docker repeats inside the container.
- **`pg_isready`** — Postgres's own "are you accepting connections?" program.
- **`CMD-SHELL`** — "run the probe text through a shell inside the container."
- **`interval` / `timeout` / `retries`** — how often / how long one try may hang / how
  many straight failures mean unhealthy.
- **`(health: starting)` / `(healthy)` / `(unhealthy)`** — no verdict yet / probe passed /
  `retries` probes in a row failed.

### Common mistakes

1. **Trusting `Up`.** `Up` means the process was launched, nothing more. Readiness is the
   healthcheck's job.
2. **Reading `unhealthy` as "the program is broken".** It means "the probe failed". The
   program may be fine and the probe wrong — see the example above. When debugging, check
   the probe command too.
3. **Expecting the healthcheck alone to make other services wait.** It only publishes a
   status. The waiting is wired separately with `depends_on` (Lesson 7).

### Where it is used

Every serious compose file gives its database a healthcheck. Kubernetes probes containers
the same way. Load balancers only send traffic to instances whose health endpoint
answers. "Started is not ready" applies for your whole career.

### Link to the task

Completes subtask 1: Postgres and Redis, named volume, healthcheck — and
`docker compose ps` showing `(healthy)` is word for word one of the task's "done when"
conditions.

### Summary for revision

- Healthcheck = repeated probe inside the container; the label is its verdict.
- `test` + `CMD-SHELL`, `interval`, `timeout`, `retries`.
- `starting` → `healthy` on first pass; `unhealthy` after `retries` straight failures.
- Redis without a healthcheck shows no health note.
- The label changes nothing by itself; `depends_on` consumes it (Lesson 7).

### Check questions, with answers

**Question 1.** Here is a healthcheck block on a Postgres service:

```yaml
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 2s
      timeout: 3s
      retries: 15
```

In plain words: what does each of the four lines do? And what label does the container
wear between its start and the first passing probe?

**Answer.** `test` is the probe: run `pg_isready -U postgres` through a shell inside the
container; it succeeds once Postgres accepts connections. `interval: 2s`: probe every two
seconds. `timeout: 3s`: if one probe hangs past three seconds, that try counts as failed.
`retries: 15`: only fifteen consecutive failures flip the label to unhealthy. Between
start and the first passing probe the container shows `(health: starting)`.

**Question 2.** A teammate's compose file has a Postgres service with a correct, working
healthcheck, plus an app service with no other changes. The app still sometimes starts
before Postgres is ready and crashes with "connection refused". The teammate says: "But I
added the healthcheck!" What does the healthcheck actually do — and what does it *not* do
by itself?

**Answer.** The healthcheck only runs the probe and publishes a label: `starting`,
`healthy`, or `unhealthy`, visible in `docker compose ps`. It does not delay, order, or
restart anything. The app service starts whenever Compose starts it, label or no label.
To make the app wait, the label must be consumed with `depends_on` plus
`condition: service_healthy` on the app service — that wiring is Lesson 7.

### Do it yourself (on a fresh laptop)

1. Add the `healthcheck:` block from this lesson to the `postgres` service in
   `zenith-stack/compose.yml`.
2. Run `docker compose up -d` and **immediately** `docker compose ps`. Expected: postgres
   shows `Up ... (health: starting)`, redis shows no health note.
3. Run `docker compose ps` again after a few seconds. Expected: `Up ... (healthy)`.
4. Optional: change the test to `["CMD-SHELL", "false"]` and `retries:` to `2`, run
   `docker compose up -d`, wait five seconds, see `(unhealthy)`. Prove the database still
   works: `docker exec zenith-stack-postgres-1 psql -U postgres -d zenith -c 'select 1;'`
   prints `1`. Restore the real test and `retries: 15`, `up -d`, confirm `(healthy)`.
5. **Done when:** you can make the label show `starting`, `healthy`, and `unhealthy` on
   demand, and explain why the unhealthy one still answered SQL.

### Your doubts

None yet.

---

## Lesson 5 — The daily loop: `up -d`, `ps`, `logs -f`, `down`, `down -v`

### Definition

Five commands for every working day:

| Command | What it does | What survives it |
| --- | --- | --- |
| `docker compose up -d` | Create and start everything from the file, in the background | — |
| `docker compose ps` | Show this project's containers and their health | — |
| `docker compose logs -f <service>` | Stream what a service prints, live | everything (it only watches) |
| `docker compose down` | Stop and remove containers and the network | **volumes, with all data** |
| `docker compose down -v` | Same, **plus delete the project's volumes** | nothing |

### Why it is needed

`up -d` when you start, `ps` when something feels off, `logs -f` to see why, `down` when
you stop. And the one flag that matters: the only difference between `down` and `down -v`
is whether the database still exists tomorrow.

### How it works, step by step

1. `up -d` creates whatever is missing: network, containers — and the volume *only if it
   does not exist yet*. An existing volume is reattached, data intact.
2. `ps` shows status, including the health label.
3. `logs <service>` prints everything the service wrote so far. `--tail 2` shows the last
   two lines. `-f` means **follow**: keep showing new lines until Ctrl+C. Ctrl+C closes
   the viewer, never the service.
4. `down` removes containers and the network. Everything inside a container dies with it —
   except what lives on a volume.
5. `down -v` additionally deletes this project's volumes (`zenith-stack_pgdata` — the
   database itself). Other projects' volumes are untouched.

### Example — real run on this machine

**`up -d` with the volume from last time.** Output listed network and containers created —
no `Volume ... Created` line: the volume was reattached. Postgres remembered:

```text
$ docker compose logs --tail 2 postgres
postgres-1  | ... LOG:  database system was shut down at 2026-11-17 17:56:39 UTC
postgres-1  | ... LOG:  database system is ready to accept connections
```

**One row goes in:**

```text
$ docker exec zenith-stack-postgres-1 psql -U postgres -d zenith -c "create table notes(id int primary key, body text);"
CREATE TABLE
$ docker exec zenith-stack-postgres-1 psql -U postgres -d zenith -c "insert into notes values (1, 'compose keeps me');"
INSERT 0 1
```

**`down`, then `up -d` again — the row survived:**

```text
$ docker exec zenith-stack-postgres-1 psql -U postgres -d zenith -c "select * from notes;"
 id |       body
----+------------------
  1 | compose keeps me
(1 row)
```

The container that wrote the row was destroyed by `down`. The row lived on the volume.

**`down -v` — two new lines in the output:**

```text
 Volume zenith-stack_pgdata  Removing
 Volume zenith-stack_pgdata  Removed
```

**`up -d` once more** printed `Volume zenith-stack_pgdata Created` — brand-new and empty:

```text
$ docker exec zenith-stack-postgres-1 psql -U postgres -d zenith -c "select * from notes;"
ERROR:  relation "notes" does not exist
```

Decoded: "relation" is Postgres's word for a table. The fresh database has never heard of
`notes`. The data was deleted, knowingly, by one flag.

### Important terms

- **`-d`** — background ("detached") start.
- **`-f` (follow)** — keep streaming new log lines until Ctrl+C.
- **`--tail N`** — show only the last N log lines.
- **`-v` on `down`** — delete this project's volumes. Volumes, not "verbose".
- **Factory reset** — `down -v` then `up -d`: a clean database in seconds, on purpose.
- **Relation** — Postgres's word for a table (appears in its error messages).

### Common mistakes

1. **Thinking `-v` means "verbose".** Here it means volumes. Typing it out of habit is
   how a morning's data disappears.
2. **Pressing Ctrl+C on `logs -f` and thinking the service stopped.** You closed the
   viewer. `docker compose ps` shows it still up.
3. **Changing `POSTGRES_PASSWORD` or `POSTGRES_DB` and seeing no effect.** Those act only
   on first initialization of an empty volume. The deliberate fix: `down -v`, then
   `up -d` — fresh volume, new values applied.

### Where it is used

The everyday loop on any project with a compose file. Factory reset of a hopeless local
database. CI starts with no volumes, so every run gets a fresh database the same way.

### Link to the task

Subtask 2 word for word: run the loop, then `down -v` and understand that `-v` deletes
the volumes and therefore the data. A real row was the witness.

### Summary for revision

- `up -d` creates only what is missing; existing volumes are reattached with data intact.
- `logs -f` follows output; Ctrl+C stops the viewer only.
- `down` keeps volumes. `down -v` deletes them — and with them, the data.
- `down -v` + `up -d` = deliberate factory reset.

### Check questions, with answers

**Question 1.** A folder `zenith-stack` holds this file:

```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_PASSWORD: dev
      POSTGRES_DB: zenith
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

volumes:
  pgdata:
```

On Monday you insert rows, and in the evening run `docker compose down`. Tuesday morning
you run `docker compose up -d` — are Monday's rows there? Tuesday evening you run
`docker compose down -v`. Wednesday morning you run `docker compose up -d` — are the rows
there now? One sentence of "why" for each.

**Answer.** Tuesday: yes. `down` removed containers and network but kept the volume, and
Tuesday's `up -d` reattached it with the rows intact. Wednesday: no. `down -v` deleted
`zenith-stack_pgdata`, so Wednesday's `up -d` created a fresh empty volume and Postgres
initialized a brand-new `zenith` database with nothing in it.

**Question 2.** You run `docker compose logs -f postgres`, watch lines stream by, and
press Ctrl+C. Did Postgres stop? Which single command shows you its current state, and
roughly what will it print?

**Answer.** No — Ctrl+C only closed the log viewer; the service never noticed.
`docker compose ps` shows the state, and it will print the postgres container as
`Up ... (healthy)`.

### Do it yourself (on a fresh laptop)

1. In `zenith-stack`, run `docker compose up -d`, wait until `docker compose ps` shows
   `(healthy)`.
2. Insert a row:
   `docker exec zenith-stack-postgres-1 psql -U postgres -d zenith -c "create table notes(id int primary key, body text);"`
   then `... -c "insert into notes values (1, 'compose keeps me');"`.
3. Run `docker compose logs -f postgres`, watch it, press Ctrl+C, then
   `docker compose ps` — postgres must still be `Up (healthy)`.
4. `docker compose down`, `docker compose up -d`, wait for healthy, then
   `... -c "select * from notes;"` — expect your row.
5. `docker compose down -v` — expect `Volume zenith-stack_pgdata Removed`.
6. `docker compose up -d`, wait, `select * from notes;` again — expect
   `ERROR: relation "notes" does not exist`.
7. **Done when:** you predicted correctly, before steps 4 and 6, whether the row would be
   there.

### Your doubts

None yet.

---

## Lesson 6 — The networking rule: service names, not `localhost`

### Definition

Two rules that are really one:

1. On a compose network, every service is reachable by its **service name**. `postgres`
   works as a machine name there.
2. Inside a container, **`localhost` means that container itself** — never your Mac,
   never another container.

### Why it is needed

The bug every developer hits once: an app connects fine to `localhost:5432` on your Mac.
The same app, same settings, inside a container — "connection refused". Nothing is wrong
with the database. The word `localhost` changed meaning when the app moved.

### How it works, step by step

1. Every `docker compose up` creates the project network (`zenith-stack_default`), and
   every service's container joins it.
2. On that network, each service name works as a machine name pointing at that service's
   container.
3. Each container is its own little computer with its own `localhost`, meaning "me". The
   word is like "home": from your house it means your house, from mine it means mine.
4. The port mapping `"5432:5432"` is a door **for your Mac only**. Containers talk to each
   other with the service name and the **container's own port**; the mapping is
   irrelevant to them.

One database, two correct addresses, each valid only where it is used:

| Who is asking | Address that works |
| --- | --- |
| Another container on the compose network | `postgres:5432` |
| Your Mac (via the mapping) | `localhost:5432` |

### Example — real run on this machine

One throwaway client container, run twice, identical except the hostname.
`docker run --network zenith-stack_default` attaches it to the project network; the image
`postgres:17-alpine` is used only because it has `psql` inside; `PGPASSWORD=dev` supplies
the password.

With `-h localhost`:

```text
psql: error: connection to server at "localhost" (127.0.0.1), port 5432 failed: Connection refused
	Is the server running on that host and accepting TCP/IP connections?
```

`localhost` resolved to the throwaway container itself; nothing listens there.

With `-h postgres`:

```text
 ?column?
----------
        1
(1 row)
```

Only the hostname changed. The service name pointed at the right container.

From the Mac, `nc -vz` asks "does anything answer at this address and port?":

```text
$ nc -vz localhost 5432
Connection to localhost port 5432 [tcp/postgresql] succeeded!

$ nc -vz postgres 5432
nc: getaddrinfo: nodename nor servname provided, or not known
```

`localhost:5432` works from the Mac — the mapping's one job. The name `postgres` does not
even resolve on the Mac; it exists only on the compose network.

Three outcomes to tell apart forever:

| Message | Meaning |
| --- | --- |
| `Connection refused` | The name resolved, a machine was reached — nothing listens on that port there |
| `nodename ... not known` | The name itself means nothing here |
| An answer | Right name, right port, right place |

### Important terms

- **Service name as machine name** — on the compose network, `postgres` addresses the
  postgres container.
- **`localhost`** — "this machine, wherever I am standing". Inside a container: that
  container.
- **`docker run --network <name>`** — attach a one-off container to an existing network.
- **Connection refused** — reached the machine; no program answers that door.
- **`getaddrinfo: nodename not known`** — the name does not resolve here at all.

### Common mistakes

1. **`localhost` in a containerized app's database URL.** Refused, every time. Fix: the
   service name, `postgres:5432`.
2. **Using the mapping's host port between containers.** With `"5433:5432"`, the Mac uses
   `localhost:5433`, but containers still use `postgres:5432` — the container's real
   port. The left number exists for the Mac alone.
3. **Expecting service names to work on your Mac.** They resolve only on the compose
   network. From the Mac: `localhost` plus the mapped host port.

### Where it is used

Every containerized app's configuration: database URLs, Redis hosts, calls between
microservices. Kubernetes works the same way — service names are machine names there too.
For any config value, ask: "who resolves this name — my Mac, or the container?"

### Link to the task

Subtask 3's rule, and the task's checkpoint question 1 word for word: `postgres:5432`
from inside compose, `localhost:5432` from the Mac, both correct from where they are
used. Lesson 7 gives the rule a permanent home in the file.

### Summary for revision

- Inside a container, `localhost` = that container. Service names reach the others.
- Port mapping serves the Mac only; containers use `servicename:containerport`.
- `Connection refused` = wrong place or nothing listening. `nodename not known` = the
  name does not exist here.

### Check questions, with answers

**Question 1.** A compose file in folder `zenith-stack`:

```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_PASSWORD: dev
      POSTGRES_DB: zenith
    ports:
      - "5432:5432"
  app:
    image: my-spring-app
```

The app's configuration says `jdbc:postgresql://localhost:5432/zenith`. Run as the `app`
service, it crashes with "connection refused" — yet the same app with the same URL works
when started directly on the Mac. Explain both facts, and write the URL that fixes the
containerized version.

**Answer.** Inside the `app` container, `localhost` means the app container itself. No
Postgres runs in there, so the connection is refused. On the Mac, `localhost` means the
Mac, where the mapping `"5432:5432"` forwards port 5432 into the postgres container — so
the same URL works. The fix for the containerized app is the service name:
`jdbc:postgresql://postgres:5432/zenith`.

**Question 2.** A compose file maps Postgres as `"5433:5432"` (two different numbers). A
second container on the same compose network wants to connect, and so does `pgcli` on
your Mac. Which address does each use?

**Answer.** The second container uses `postgres:5432` — service name plus the container's
real port; the mapping does not apply between containers. `pgcli` on the Mac uses
`localhost:5433` — the mapped host port is the Mac's door.

### Do it yourself (on a fresh laptop)

1. In `zenith-stack`, run `docker compose up -d`, wait for `(healthy)`.
2. Fail on purpose:

   ```bash
   docker run --rm --network zenith-stack_default -e PGPASSWORD=dev \
     postgres:17-alpine psql -h localhost -U postgres -d zenith -c 'select 1;'
   ```

   Expected: `connection to server at "localhost" ... Connection refused`.
3. Change only `-h localhost` to `-h postgres`. Expected: a table with `1`.
4. From the laptop: `nc -vz localhost 5432` — expect `succeeded`. Then
   `nc -vz postgres 5432` — expect `getaddrinfo: nodename nor servname provided, or not
   known`. With `pgcli`: `pgcli postgresql://postgres:dev@localhost:5432/zenith`, then
   `select 1;`.
5. `docker compose down` when finished.
6. **Done when:** before each command, you can say which address will work from where,
   and why.

### Your doubts

None yet.

---

## Lesson 7 — `depends_on` with `condition: service_healthy`

### Definition

`depends_on` is a service setting meaning "start that one before me". Two strengths:

- Plain `depends_on` waits only for the other **container to start existing**.
- With `condition: service_healthy`, it waits until the other container's **healthcheck
  passes**.

### Why it is needed

A container exists within milliseconds; the program inside needs seconds. "Start the
database first, then my app" only orders the container launches, not the readiness — so
the app can still connect into the gap and crash with "connection refused". The classic
first-job bug.

### How it works, step by step

Two pieces cooperate:

1. The **healthcheck** (Lesson 4) probes the program and publishes a label: `starting`,
   then `healthy`.
2. **`depends_on` with a condition** consumes the label: Compose holds the dependent
   service in `Waiting` and starts it only on `healthy`.

Weak form (waits for container start only):

```yaml
    depends_on:
      - postgres
```

Strong form (waits for readiness):

```yaml
    depends_on:
      postgres:
        condition: service_healthy
```

Healthcheck without `depends_on`: a label nobody reads. `depends_on` without healthcheck:
waiting for the wrong event.

### Example — real run on this machine

The third service added to `zenith-stack/compose.yml` — a one-shot client:

```yaml
  dbcheck:
    image: postgres:17-alpine
    environment:
      PGPASSWORD: dev
    depends_on:
      postgres:
        condition: service_healthy
    command: psql -h postgres -U postgres -d zenith -c 'select now();'
```

`command:` tells the container what to run instead of the image's default program (the
full story of defaults is the ENTRYPOINT/CMD lesson in the Dockerfile half). `-h postgres`
is the networking rule written permanently into the file. The service runs one `psql`,
prints, exits — a "job" container.

**Run 1 — weak form, freshly wiped volume (`down -v` first, slow cold boot):**

```text
 Container zenith-stack-postgres-1  Started
 Container zenith-stack-dbcheck-1  Starting
 Container zenith-stack-dbcheck-1  Started
```

`dbcheck` started the instant the postgres *container* existed. Four seconds later:

```text
$ docker compose ps -a
zenith-stack-dbcheck-1   dbcheck   Exited (2) 4 seconds ago
zenith-stack-postgres-1  postgres  Up 4 seconds (healthy)

$ docker compose logs dbcheck
dbcheck-1  | psql: error: connection to server at "postgres" (172.18.0.3), port 5432 failed: Connection refused
```

The name `postgres` **resolved** to an IP — naming worked; readiness did not. Postgres
became healthy four seconds too late.

**Run 2 — strong form, same wiped-volume start.** Two new lines, in this order:

```text
 Container zenith-stack-postgres-1  Started
 Container zenith-stack-postgres-1  Waiting
 Container zenith-stack-postgres-1  Healthy
 Container zenith-stack-dbcheck-1  Starting
```

Compose held `dbcheck` back until the label said healthy. Payoff:

```text
$ docker compose logs dbcheck
dbcheck-1  |               now
dbcheck-1  | -------------------------------
dbcheck-1  |  2026-11-17 18:05:34.377986+00
```

**Run 3 — a condition that can never be met** (dependency pointed at redis, which has no
healthcheck):

```text
dependency failed to start: container zenith-stack-redis-1 has no healthcheck configured
```

Compose refuses rather than wait forever for a verdict that will never come.

### Important terms

- **`depends_on`** — "start that one before me". Order only, unless given a condition.
- **`condition: service_healthy`** — start me only when that service's healthcheck
  passes.
- **`Waiting` / `Healthy`** — the up output lines showing the gate in action.
- **One-shot (job) container** — runs a command, prints, exits. `Exited (0)` = success;
  nonzero = failure.

### Common mistakes

1. **Plain `depends_on`, believing it waits for readiness.** It waits for container
   existence — milliseconds. The race was lost by four seconds.
2. **`condition: service_healthy` on a service with no healthcheck.** `dependency failed
   to start: ... has no healthcheck configured`.
3. **Thinking `depends_on` is about networking.** Reachability comes from the shared
   network, always. `depends_on` only shapes when things start.

### Where it is used

App-waits-for-database in nearly every compose file. Migration jobs (like Flyway) that
must run against a ready database. CI pipelines gating tests on a healthy database.

### Link to the task

Completes subtask 3: a service connecting at `postgres:5432` that starts only when the
healthcheck passes — the task's "dependent services wait" promise. Answers checkpoint
question 6.

### Summary for revision

- Plain `depends_on` = start order only. Container exists ≠ program ready.
- `condition: service_healthy` = genuine wait, visible as `Waiting` → `Healthy` in up
  output.
- The pair: healthcheck publishes readiness; `depends_on` consumes it.
- Demanding `service_healthy` from a service without a healthcheck is an immediate error.

### Check questions, with answers

**Question 1.** A teammate's compose file:

```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_PASSWORD: dev
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 2s
      timeout: 3s
      retries: 15
  app:
    image: my-spring-app
    depends_on:
      - postgres
```

On a slow morning the app crashes with "connection refused", even though postgres has a
working healthcheck. What exactly did `depends_on` wait for, why did the app still crash,
and what does the fixed block look like?

**Answer.** The plain list form waited only for the postgres *container* to start — which
takes milliseconds. The healthcheck label existed but nothing consumed it, so the app
started while Postgres was still booting, connected into the gap, and was refused. Fix:

```yaml
    depends_on:
      postgres:
        condition: service_healthy
```

**Question 2.** Same file with the fix applied, healthcheck probing every 2 seconds with
up to 15 retries. On a cold morning Postgres needs about 9 seconds before it accepts
connections. What does `docker compose up -d` do with the `app` service, and roughly when
does it start? What if Postgres never became ready at all?

**Answer.** Compose starts postgres, holds `app` in `Waiting`, and probes every 2
seconds. Around the 9-second mark a probe passes, the label flips to `healthy`, and `app`
starts immediately — roughly 10 seconds in. If Postgres never became ready, 15 straight
failures would mark it `unhealthy`, and Compose would abort with `dependency failed to
start`; `app` would never start at all.

### Do it yourself (on a fresh laptop)

1. Add the `dbcheck` service from this lesson to `zenith-stack/compose.yml`, first with
   the weak form (`depends_on:` then `- postgres`).
2. `docker compose down -v`, then `docker compose up -d`. Expected: `dbcheck Starting`
   right after `postgres Started`; `docker compose ps -a` shows `dbcheck Exited (2)`;
   `docker compose logs dbcheck` shows `Connection refused`.
3. Change to the strong form (`postgres:` with `condition: service_healthy`).
   `docker compose down -v`, `docker compose up -d`. Expected order in output:
   `postgres Waiting`, `postgres Healthy`, `dbcheck Starting`. Logs show a timestamp;
   `ps -a` shows `dbcheck Exited (0)`.
4. Optional: point the dependency at `redis:` with `condition: service_healthy`, run
   `up -d`, expect `dependency failed to start: ... has no healthcheck configured`.
   Revert.
5. `docker compose down`.
6. **Done when:** you can explain `Exited (2)` vs `Exited (0)`, and why step 2's error
   showed an IP address next to the name `postgres`.

### Your doubts

None yet.

---

## Lesson 8 — Proof under the hood: the network, the DNS names, the health record

### Definition

Three inspection commands that show the machinery:

- `docker network ls` — list every network Docker knows about.
- `docker network inspect <name>` — one network's members and their addresses.
- `docker inspect <container>` — one container's full record, including stored health
  history under `State.Health`.

### Why it is needed

Two things were taken on faith: that the name `postgres` reaches the right container, and
that `(healthy)` comes from somewhere. Inspection removes the faith — and it is the
everyday debugging tool for "what is actually true right now?".

### How it works, step by step

1. The compose network is a real, named object, listed next to Docker's built-ins:
   `bridge` (default for plain `docker run`), `host`, `none`.
2. Every running container on a network holds a spot with its own private IP.
3. **DNS** is a phone book: give it a name, get an IP. Docker runs a tiny DNS service on
   every network it creates for a project; each service name is an entry. The default
   `bridge` has **no** phone book.
4. Probe results are stored: time, exit code, output of every healthcheck run live in the
   container record under `State.Health`.

### Example — real run on this machine

**The network exists:**

```text
$ docker network ls
NETWORK ID     NAME                   DRIVER    SCOPE
06fe85a17317   bridge                 bridge    local
847e9bf7caf1   host                   host      local
67853f34c868   none                   null      local
3d0fe43fb583   zenith-stack_default   bridge    local
```

**The members and their IPs:**

```text
$ docker network inspect zenith-stack_default --format '{{range .Containers}}{{.Name}}  {{.IPv4Address}}{{"\n"}}{{end}}'
zenith-stack-postgres-1  172.18.0.3/16
zenith-stack-redis-1  172.18.0.2/16
```

(`dbcheck` is absent: it ran and exited; only running containers hold a spot.)

**The phone book answers.** `getent hosts <name>` asks the machine's phone book. On the
network:

```text
$ docker run --rm --network zenith-stack_default postgres:17-alpine getent hosts postgres
172.18.0.3        postgres  postgres
```

Exactly the IP `network inspect` listed. Off the network (plain `docker run`, default
bridge):

```text
$ docker run --rm postgres:17-alpine getent hosts postgres
(no output)
exit code: 2
```

No phone book entry, no answer — the mechanism behind Lesson 6's rule, and behind the
Mac's `nodename not known`. Lesson 7's failed `dbcheck` printing `"postgres"
(172.18.0.3)` was the phone book answering correctly milliseconds before the refusal:
name resolution and readiness are separate things.

**The health record:**

```text
$ docker inspect zenith-stack-postgres-1 --format '{{json .State.Health}}'
{
    "Status": "healthy",
    "FailingStreak": 0,
    "Log": [
        { "Start": "...", "ExitCode": 0,
          "Output": "/var/run/postgresql:5432 - accepting connections\n" },
        ...
```

`Status` is the current verdict — the very field `docker compose ps` prints as
`(healthy)`. `FailingStreak` counts consecutive failures (vs `retries`). `Log` holds one
entry per probe with its exit code and printed output.

### Important terms

- **DNS** — a phone book: name in, IP out.
- **`getent hosts <name>`** — ask this machine's phone book.
- **Default `bridge`** — Docker's default network; containers get IPs but there is no
  name lookup between them.
- **`State.Health`** — the stored healthcheck record: `Status`, `FailingStreak`, `Log`.

### Common mistakes

1. **Hardcoding a container's IP.** IPs are handed out fresh on every `up`. Names
   re-resolve every time — that is why config uses names.
2. **Expecting name lookup on the default bridge.** Service names resolve only on created
   networks.
3. **Debugging `(unhealthy)` by restarting.** Read `State.Health.Log` first — the probe's
   own output usually names the problem.

### Where it is used

Reachability debugging ("are both actually on the network?"), health forensics (the
probe's stored output), and the same name→IP system scaled up is how Kubernetes services
find each other.

### Link to the task

Subtask 4 word for word: `network ls`, `network inspect` with each service's IP, why
`postgres` resolves inside and means nothing on the Mac, and the `Health` section saying
`healthy`. Completes the Compose half: subtasks 1–4 done.

### Summary for revision

- The compose network is a listed, inspectable object; members each have a private IP.
- Service names are DNS entries on that network only. Default bridge: no phone book.
- IPs change per `up`; names re-resolve. Config uses names.
- `compose ps`'s `(healthy)` is `State.Health.Status`; the probe history is in
  `State.Health.Log`.

### Check questions, with answers

**Question 1.** On a running stack:

```text
$ docker network inspect zenith-stack_default --format '...'
zenith-stack-postgres-1  172.18.0.3/16

$ docker run --rm --network zenith-stack_default postgres:17-alpine getent hosts postgres
172.18.0.3        postgres
```

A teammate sets the app's database host to `172.18.0.3` — "it works, I tested it". What
breaks, and when? What should they use instead, and why does it keep working?

**Answer.** It breaks on the next `docker compose down` and `up` (or any recreate): the
new network hands out fresh IPs, and postgres may come up at a different address — while
`172.18.0.3` now points at nothing, or at a different container entirely. They should use
the service name `postgres`: it is a phone book entry that re-resolves to the current IP
on every lookup, so it is correct after every restart.

**Question 2.** `docker compose ps` shows `shop-api-1` as `Up 2 minutes (unhealthy)`. You
run:

```text
$ docker inspect shop-api-1 --format '{{json .State.Health}}'
{
  "Status": "unhealthy",
  "FailingStreak": 19,
  "Log": [
    { "ExitCode": 1, "Output": "curl: (7) Failed to connect to localhost port 8080: Connection refused\n" }
  ]
}
```

What do `Status`, `FailingStreak`, and the `Log` entry each tell you? What is the probe
actually reporting?

**Answer.** `Status: unhealthy` is the current verdict — the same word `compose ps`
shows. `FailingStreak: 19` means the probe has failed 19 times in a row. The `Log` entry
shows the probe ran `curl` against `localhost:8080` *inside* the container (its own
localhost — correct usage, same container) and was refused, exiting with code 1. The
probe reports: no program is listening on port 8080 inside this container — the app
either crashed or listens on a different port.

### Do it yourself (on a fresh laptop)

1. In `zenith-stack`: `docker compose up -d`, wait for `(healthy)`.
2. `docker network ls` — expect `bridge`, `host`, `none`, and `zenith-stack_default`.
3. `docker network inspect zenith-stack_default --format '{{range .Containers}}{{.Name}}  {{.IPv4Address}}{{"\n"}}{{end}}'`
   — note postgres's IP.
4. `docker run --rm --network zenith-stack_default postgres:17-alpine getent hosts postgres`
   — expect the exact IP from step 3. Repeat without `--network ...` — expect no output,
   exit code 2.
5. `docker inspect zenith-stack-postgres-1 --format '{{json .State.Health}}'` — find
   `"Status": "healthy"` and probe entries saying `accepting connections`.
6. `docker compose down`.
7. **Done when:** you can trace: service name → phone book entry → IP in `network
   inspect` → container, and point at where `compose ps` gets `(healthy)`.

### Your doubts

None yet.

---

## Lesson 9 — What a Dockerfile is, and why it exists

### Definition

A **Dockerfile** is a text file of instructions. `docker build` follows those
instructions, top to bottom, and the result is an **image** — a frozen, ready-to-run copy
of a program and everything it needs. A **container** is one running instance made from an
image.

### Why it is needed

Every image so far (`postgres:17-alpine`, `redis:7-alpine`) was pulled from a public
library of images other people built. Your own application has no image, so Docker cannot
run it and it cannot join the compose file. Today it runs only like this:

```text
$ java -jar target/zenith-catalog.jar
... Tomcat started on port 8080 (http) with context path '/'
... Started ZenithCatalogApplication in 1.026 seconds

$ curl localhost:8080/
Zenith Catalog is running
```

That works for one reason: this Mac happens to have Java 21. A teammate with Java 17: it
fails. A fresh CI machine with no Java: it fails. "Install Java 21 first" becomes a wiki
page and machines drift. The Dockerfile is a recipe, checked into git, that packs the
program **and its Java and its settings** into one image, which any machine with Docker
runs identically.

### How it works, step by step

The one idea that matters most:

- **Build time, once:** `docker build` reads the Dockerfile and produces a frozen image.
- **Run time, many times:** containers start from the frozen image. The Dockerfile is not
  involved anymore; running a container never changes the image.

Recipe-card picture: start from a store-bought base (an image that already has Java), add
your ingredient (the jar), write the serving instruction (how to start the program).
Docker executes the card once and shrink-wraps the result.

Preview of the actual five-line recipe (read line by line in Lesson 10):

```dockerfile
FROM eclipse-temurin:21-jre-alpine            # start from a base that has Java 21
WORKDIR /app                                  # work in folder /app inside the image
COPY target/zenith-catalog.jar app.jar        # put my jar into the image
EXPOSE 8080                                   # note: the app will use port 8080
ENTRYPOINT ["java", "-jar", "app.jar"]        # the program a container runs
```

### The application this task containerizes

Created in the task folder as `zenith-catalog/`. Three files.

`pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.16</version>
        <relativePath/>
    </parent>

    <groupId>com.zenith</groupId>
    <artifactId>zenith-catalog</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>zenith-catalog</finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

`src/main/java/com/zenith/catalog/ZenithCatalogApplication.java`:

```java
package com.zenith.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class ZenithCatalogApplication {

    private final JdbcTemplate jdbc;

    public ZenithCatalogApplication(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static void main(String[] args) {
        SpringApplication.run(ZenithCatalogApplication.class, args);
    }

    /** Answers without touching the database. Proves the web server is up. */
    @GetMapping("/")
    public String home() {
        return "Zenith Catalog is running\n";
    }

    /** Asks the database for the time. Proves the app can reach Postgres. */
    @GetMapping("/db")
    public String db() {
        return "db says: " + jdbc.queryForObject("select now()", String.class) + "\n";
    }
}
```

`src/main/resources/application.properties`:

```properties
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/zenith}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:postgres}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:dev}
```

Build and smoke test, real output:

```text
$ mvn -q package -DskipTests
$ ls -lh target/zenith-catalog.jar
-rw-r--r--  22M  target/zenith-catalog.jar
```

22 MB because it is a **fat jar** (from Task F1.5): your classes plus every dependency jar
plus Tomcat, in one file. One file is exactly what a recipe wants to pick up.

### Important terms

- **Dockerfile** — the build recipe for an image.
- **`docker build`** — execute the recipe once; output: an image.
- **Build time vs run time** — recipe runs once at build; containers run the frozen
  result many times.
- **Registry** — the public library images are pulled from.
- **Fat jar** — one jar holding your classes, all dependencies, and the server.

### Common mistakes

1. **Thinking the Dockerfile runs every time a container starts.** It runs once, at
   build. Recipe changes do nothing until a rebuild.
2. **Editing source and re-running the container, expecting the change.** The image froze
   yesterday's jar. New code needs: rebuild jar, rebuild image, run again.
3. **Thinking an image is a little virtual machine.** It is files plus a note of what to
   run — which is why images are small and start in milliseconds.

### Where it is used

Every backend service ships as an image built from a Dockerfile (or close cousin). CI
builds it on every merge; production runs it. Phase 13 is this pipeline,
industrial-strength.

### Link to the task

Stages subtask 5: the application exists in the task folder and builds to
`target/zenith-catalog.jar`. The recipe is Lesson 10.

### Summary for revision

- Dockerfile = recipe; build once → frozen image; run many times.
- Images exist so a program carries its own Java and settings to any machine.
- Our app: `zenith-catalog`, fat jar at `target/zenith-catalog.jar`, endpoints `/` (no
  database) and `/db` (asks Postgres the time).

### Check questions, with answers

**Question 1.** A teammate ships a Spring Boot app as `app.jar` plus a note: "install
Java 21, then run `java -jar app.jar`". It must run on three machines: your laptop (Java
17), a fresh CI server (no Java), and production (Java 21). What happens on each? Name two
problems a Docker image would solve.

**Answer.** Laptop: the jar was built for Java 21, so Java 17 refuses to start it with a
class-version error. CI: `java: command not found` — nothing installed. Production: runs.
An image solves: (1) the program carries its own Java 21 inside, so no machine needs the
right Java installed and versions cannot drift; (2) every machine runs the identical
frozen bytes with one `docker run` command — no manual setup steps, no wiki page. (Also:
the recipe lives in git, reviewable and repeatable.)

**Question 2.** Yesterday you built an image from this Dockerfile:

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/zenith-catalog.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Today you edit `ZenithCatalogApplication.java`, save, and immediately run a container from
yesterday's image. Does your edit show up? Why or why not — and which steps, in order,
make it show up?

**Answer.** No. The image is frozen: it contains the jar as it existed at build time, and
running a container never changes an image. The edit is only in your source file — not
even in the jar yet. In order: `mvn package` (bake the edit into a new jar), then
`docker build` (bake the new jar into a new image), then run a container from that new
image.

### Do it yourself (on a fresh laptop)

1. Create the `zenith-catalog` folder with the three files shown above (pom, application
   class, properties).
2. Build: `mvn -q package -DskipTests`, then `ls -lh target/zenith-catalog.jar` — expect
   about 22 MB. (Wrong folder gives `Unable to access jarfile` later; missing Maven:
   `brew install maven`.)
3. Run: `java -jar target/zenith-catalog.jar` — expect `Tomcat started on port 8080`.
4. Second terminal: `curl localhost:8080/` — expect `Zenith Catalog is running`. Stop
   with Ctrl+C.
5. **Done when:** the jar builds, `curl` answers, and you can say why this app, shipped
   as a bare jar, still depends on the laptop having Java 21.

### Your doubts

None yet.

---

## Lesson 10 — The first Dockerfile, line by line

### Definition

The five-instruction recipe that turns the jar into an image. Each instruction states one
fact about the image:

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/zenith-catalog.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Lives at `zenith-catalog/Dockerfile`.

### Why it is needed

Each line answers a question asked of every image: What do you start from? Where do you
work? What goes in? Which port? What runs? Copy-pasting without reading is how people end
up believing `EXPOSE` opens ports, or shipping compiler-laden images.

### How it works, step by step

1. **`FROM eclipse-temurin:21-jre-alpine`** — the **base image**: someone's finished
   image as your starting point. Four readable parts: *eclipse-temurin* = a project
   publishing ready Java images; *21* = Java version; *jre* = Java Runtime Environment,
   enough to **run** Java, no compiler (*jdk* would add the compiler); *alpine* = a tiny
   Linux underneath. Proof the base carries Java — run on this machine, empty, no app:

   ```text
   $ docker run --rm eclipse-temurin:21-jre-alpine java --version
   openjdk 21.0.13 2026-10-20 LTS
   ```

   The base alone weighs 287 MB (`docker images eclipse-temurin:21-jre-alpine`).
2. **`WORKDIR /app`** — set the current folder inside the image, creating it if needed. A
   `cd` that sticks: later instructions and the running program operate from `/app`.
3. **`COPY target/zenith-catalog.jar app.jar`** — two paths, two worlds. Left: on your
   machine, read relative to the folder handed to `docker build` (the build context).
   Right: inside the image, relative to the working folder → the file lands as
   `/app/app.jar`, renamed in flight so the ENTRYPOINT never depends on the project's jar
   name.
4. **`EXPOSE 8080`** — a **note, not an action**. Documents "this program listens on
   8080". It does not connect anything to your Mac; `-p 8080:8080` or a `ports:` block
   does that, always.
5. **`ENTRYPOINT ["java", "-jar", "app.jar"]`** — the program a container runs at start.
   Bracket form = a list, one word per element: `java -jar app.jar`, executed in `/app`.
   (Its pairing with CMD and the no-brackets trap are Lessons 13 and 14.)

### Example — real state on this machine

`zenith-catalog/` holds `Dockerfile`, `pom.xml`, `src/`, and `target/zenith-catalog.jar`
(22 MB). Base image pulled and verified (output above).

### Important terms

- **Base image** — the finished image a `FROM` line starts from.
- **jre vs jdk** — run-only Java vs Java-plus-compiler. Running a jar needs only jre.
- **alpine** — a tiny Linux used as the bottom of small images.
- **`WORKDIR`** — sticky `cd` inside the image.
- **Build context** — the folder handed to `docker build`; COPY's left paths are read
  from it.
- **`EXPOSE`** — documentation of the listening port. Publishes nothing.
- **Exec (bracket) form** — the list form of ENTRYPOINT: `["java", "-jar", "app.jar"]`.

### Common mistakes

1. **Believing `EXPOSE` publishes the port.** It documents. Without `-p`/`ports:`, the
   Mac cannot reach the container.
2. **`COPY` with an absolute Mac path or `../outside`.** The left path must live inside
   the build folder; anything else fails the build.
3. **Using a `jdk` image just to run a jar.** Works, but ships an unused compiler: bigger
   download, bigger attack surface. Run on `jre`; compile elsewhere (multi-stage, Lesson
   16).

### Where it is used

The standard single-stage recipe for every jar-based service. The multi-stage upgrade
(Lesson 16) is the production form, but this shape is the one to know from memory.

### Link to the task

Subtask 5 done: the Dockerfile exists, character for character as the task specifies.

### Summary for revision

- FROM = starting point (jre-alpine: small, run-only Java). WORKDIR = sticky cd.
- COPY left = your machine (inside the build folder); right = inside the image.
- EXPOSE documents; `-p`/`ports:` publishes.
- ENTRYPOINT (bracket list) = the program the container runs.

### Check questions, with answers

**Question 1.** An image is built from exactly the five-line Dockerfile above. A teammate
runs `docker run --rm zenith-catalog`, then on the same Mac `curl localhost:8080/` fails
to connect. "Impossible — the Dockerfile says EXPOSE 8080!" Explain what `EXPOSE` did and
did not do, and give the corrected run command.

**Answer.** `EXPOSE` only wrote a note into the image: "the program here listens on
8080". It is documentation for humans and tools. It did not connect the container's 8080
to the Mac — only a port mapping does that, and none was given. Corrected:
`docker run --rm -p 8080:8080 zenith-catalog`, after which `curl localhost:8080/`
answers.

**Question 2.** Same Dockerfile. (a) After the build, what is the full path of the jar
inside the image, and which two lines decide that? (b) You run the build from a folder
whose `target/` contains no `zenith-catalog.jar` — what happens, and what must you run
first?

**Answer.** (a) `/app/app.jar`. `WORKDIR /app` sets the folder; `COPY ... app.jar` puts
the file there under the new name. (b) The build fails at the COPY step with a "not
found" error — Docker cannot copy what does not exist in the build folder. Run
`mvn -q package -DskipTests` first so `target/zenith-catalog.jar` exists, then build.

### Do it yourself (on a fresh laptop)

1. In `zenith-catalog` (jar already built), create `Dockerfile` with the five lines.
2. `docker pull eclipse-temurin:21-jre-alpine`, then
   `docker run --rm eclipse-temurin:21-jre-alpine java --version` — expect
   `openjdk 21.0.x`.
3. `docker images eclipse-temurin:21-jre-alpine` — note the size (~287 MB) for the later
   size comparison.
4. `ls -lh target/zenith-catalog.jar` — the file COPY points at must exist.
5. **Done when:** covering the file with your hand, you can say what each line does —
   including what `EXPOSE` does *not* do.

### Your doubts

None yet.

---

## Lesson 11 — `docker build` and layers

### Definition

`docker build -t zenith-catalog .` executes the recipe and produces the image. A **layer**
is one filesystem change produced by one instruction. An **image is a stack of layers** —
yours on top, the base image's below.

### Why it is needed

Build output is a story: what Docker did, what it skipped, what failed at which line.
Layers are the mental model behind rebuild speed (cache), instruction ordering, and image
size.

### How it works, step by step

1. `-t zenith-catalog` — **tag**: name the image. Without it, the image is nameless.
2. `.` — the **build context**: the folder handed to Docker. The builder receives *a copy
   of this folder and nothing else*. COPY left paths are looked up inside it; the rest of
   the Mac does not exist for the build.
3. Docker reads the Dockerfile, transfers the context, executes file-changing
   instructions as numbered steps, and exports the result as a named image.
4. Instructions that change no files (`EXPOSE`, `ENTRYPOINT`) write only metadata — they
   appear as 0 B rows in the layer stack.

Builder note: this machine first used Docker's deprecated "legacy builder" (`Step 1/5`
style output). The modern builder, **BuildKit** (installed via `brew install
docker-buildx` plus a symlink into `~/.docker/cli-plugins`), is what Docker Desktop and CI
use. Its outputs — and its word `CACHED` — are the ones in these notes.

### Example — real run on this machine

```text
$ docker build -t zenith-catalog .
#1 [internal] load build definition from Dockerfile
#5 [internal] load build context
#5 transferring context: 23.19MB 0.1s done
#4 [1/3] FROM docker.io/library/eclipse-temurin:21-jre-alpine@sha256:ac05...
#6 [2/3] WORKDIR /app
#7 [3/3] COPY target/zenith-catalog.jar app.jar
#8 exporting to image
#8 naming to docker.io/library/zenith-catalog:latest done
```

The context is ~23 MB because the jar sits in the folder. Only the three file-changing
instructions get numbered steps. The layer stack:

```text
$ docker history zenith-catalog
ENTRYPOINT ["java" "-jar" "app.jar"]     0B        ← metadata only
EXPOSE [8080/tcp]                        0B        ← metadata only
COPY target/zenith-catalog.jar app.jar   23.2MB    ← our jar
WORKDIR /app                             8.19kB    ← the created folder
ENTRYPOINT ["/__cacert_entrypoint.sh"]   0B        ┐
RUN ... ARCH= ...                        163MB     │ base image's own
RUN ... apk add ...                      40.6MB    │ layers (Java!)
...                                                ┘
```

Sizes add up — nothing mysterious:

```text
zenith-catalog:latest          330MB    (= 287 base + 23 jar + small change)
eclipse-temurin:21-jre-alpine  287MB
```

The milestone run, and the EXPOSE proof:

```text
$ docker run -d -p 8080:8080 --name zc zenith-catalog
$ curl localhost:8080/
Zenith Catalog is running          ← the image's own Java 21 did this

$ docker run -d --name zc2 zenith-catalog     (no -p)
$ curl localhost:8080/
curl: failed to connect to localhost:8080     ← EXPOSE published nothing
```

### Important terms

- **Tag (`-t`)** — the image's name.
- **Build context (`.`)** — the folder copied to the builder; the build's whole world.
- **BuildKit** — the modern builder; prints `CACHED` for reused layers.
- **`docker history <image>`** — the layer stack with per-layer sizes.
- **Metadata layer** — a 0 B row from an instruction that changed no files.

### Common mistakes

1. **`COPY` pointing outside the build context.** Added on purpose:
   `COPY ../zenith-stack/compose.yml /tmp/x` — the file exists on the Mac, and still:

   ```text
   ERROR: failed to build: ... "/zenith-stack/compose.yml": not found
   ```

   The builder only has the copied context; there is no "one level up" in its world
   (Docker even stripped the `..`). Everything COPY needs must live inside the build
   folder.
2. **Forgetting the dot.**

   ```text
   Usage:  docker buildx build [OPTIONS] PATH | URL | -
   ```

   Docker asks "build what folder?" — the dot answers "this one".
3. **Expecting `EXPOSE` to publish.** Proven false above.

### Where it is used

Reading build output in CI logs is routine debugging. `docker history` answers "why is
this image so big?" — the key question of the multi-stage lesson.

### Link to the task

Subtask 6, first half: built with `docker build -t zenith-catalog .` and the output read.
Refinement: file-changing instructions make layers; EXPOSE/ENTRYPOINT write 0 B metadata.
Second half (rebuild → `CACHED`) is Lesson 12.

### Summary for revision

- `-t` names; `.` is the build context — the build's entire world.
- Image = stack of layers; read it with `docker history`; sizes add up.
- EXPOSE/ENTRYPOINT: 0 B metadata. COPY: a real layer the size of what it copied.
- The app ran under Docker with `-p 8080:8080`; without `-p` it is unreachable.

### Check questions, with answers

**Question 1.** A build of the five-line Dockerfile prints `transferring context:
23.19MB`, and `docker history` shows:

```text
ENTRYPOINT ["java" "-jar" "app.jar"]     0B
EXPOSE [8080/tcp]                        0B
COPY target/zenith-catalog.jar app.jar   23.2MB
WORKDIR /app                             8.19kB
```

Which instruction created the 23.2 MB layer? Why do the top two rows say 0 B? Why was the
transferred context about 23 MB?

**Answer.** The COPY instruction created the 23.2 MB layer — it added the fat jar's bytes
to the image. ENTRYPOINT and EXPOSE are 0 B because they change no files; they only write
metadata (the start command, the documented port) into the image's config. The context was
~23 MB because the build sends a copy of the whole folder, and the folder contains the jar
in `target/`.

**Question 2.** In folder `zenith-catalog`, a Dockerfile line reads
`COPY ../shared/config.yml /app/config.yml`. The file `../shared/config.yml` truly exists
on the Mac, yet the build fails with `"/shared/config.yml": not found`. Why can Docker not
see a file that exists, and what rule fixes this?

**Answer.** `docker build .` hands the builder a copy of the current folder only — that
copy is the build's entire world, and there is no "one level up" inside it. A path
starting with `../` escapes the context, so Docker cannot find it, however real it is on
the Mac. Rule: everything `COPY` references must live inside the build context — move the
file into the folder, or run the build from a parent folder that contains both.

### Do it yourself (on a fresh laptop)

1. In `zenith-catalog`: `docker build -t zenith-catalog .` and read each block of output.
2. `docker images` — expect roughly 330 MB for `zenith-catalog` next to the 287 MB base.
   `docker history zenith-catalog` — find the COPY layer and the two 0 B rows.
3. `docker run -d -p 8080:8080 --name zc zenith-catalog`, then `curl localhost:8080/` —
   expect `Zenith Catalog is running`. Clean up: `docker rm -f zc`.
4. Run again without `-p` and `curl` — expect a connection failure. Clean up.
5. Break it both ways: add `COPY ../anything .`, build, read `not found`, remove the
   line. Run `docker build -t zenith-catalog` without the dot, read the usage error.
6. **Done when:** you can explain the image's size from its history and name the two
   instructions that produced no layer.

### Your doubts

None yet.

---

## Lesson 12 — The build cache: why rebuilds take 0.3 seconds

### Definition

The **build cache** is Docker reusing a layer from a previous build because the
instruction and its inputs have not changed. A reused layer prints `CACHED`.

### Why it is needed

Images are rebuilt constantly — every local change, every CI push. The cache skips
unchanged work, turning minutes into seconds. Knowing *what invalidates it* is the
superpower behind the layer-ordering rule.

### How it works, step by step

For each instruction, top to bottom, Docker asks:

1. Is the **instruction text** identical to last time?
2. Are the **input files** identical — for `COPY`, does the *content checksum* match?
   (Content, not timestamps.)
3. Was **every instruction before it** also served from cache?

Three yeses → `CACHED`. Any no → the instruction runs for real, **and every instruction
after it runs for real too** — each layer is built on the one before. One changed layer
invalidates the rest of the stack (the chain rule).

### Example — real run on this machine

**Run 1 — nothing changed:**

```text
$ docker build -t zenith-catalog .
#4 transferring context: 75B done
#6 [2/3] WORKDIR /app
#6 CACHED
#7 [3/3] COPY target/zenith-catalog.jar app.jar
#7 CACHED
```

Total 0.3 seconds. Note `transferring context: 75B` — the previous build sent 23.19 MB;
this time BuildKit saw no file changed and sent bookkeeping only. The cache thinks in
content everywhere.

**Run 2 — one real source change** (a `/ping` endpoint added, `mvn -q package
-DskipTests`, build):

```text
#5 transferring context: 23.19MB 0.1s done
#6 [2/3] WORKDIR /app
#6 CACHED
#7 [3/3] COPY target/zenith-catalog.jar app.jar
#7 DONE 0.0s
```

`FROM`: same pinned base — cached. `WORKDIR`: same text, prior steps cached — `CACHED`.
`COPY`: same text, but the jar's checksum changed — rebuilt (`DONE`). Nothing after COPY
makes file layers, so the chain rule had nothing more to rebuild — this time.

### Important terms

- **`CACHED`** — this layer was reused from a previous build.
- **Content checksum** — the fingerprint of a file's bytes; what COPY's cache check
  compares.
- **Chain rule** — the first rebuilt layer forces every later layer to rebuild too.
- **`--no-cache`** — force a full rebuild; for rare weirdness, not daily use.

### Common mistakes

1. **Believing the cache keys on timestamps.** It keys on instruction text and content
   checksums. `touch` busts nothing; different bytes do.
2. **Fearing `CACHED` is stale and spraying `--no-cache`.** Checksums make reuse correct
   by construction; habitual `--no-cache` only makes builds slow.
3. **Forgetting the chain rule.** Changing an early line (say, the `FROM` tag) rebuilds
   everything after it, changed or not. Order is destiny — the ordering lesson.

### Where it is used

Every CI pipeline: cache hits are speed and money. Locally: instant rebuild-and-test
versus coffee breaks.

### Link to the task

Completes subtask 6: built, rebuilt unchanged, and watched every buildable line say
`CACHED`.

### Summary for revision

- Cache check per instruction: same text? same input content? all prior steps cached?
- First miss rebuilds that layer and everything after it.
- BuildKit even skips re-sending unchanged context (75 B vs 23 MB).
- Rebuilding the jar with changed source busts exactly the COPY layer here.

### Check questions, with answers

**Question 1.** A teammate rebuilds the five-line Dockerfile with zero changes anywhere.
Output shows `CACHED` on WORKDIR and COPY, finishing in under a second. They ask: "How did
Docker *know* it was safe to skip the COPY — what if the jar was different?" What exactly
did Docker compare?

**Answer.** Three things. The instruction text (`COPY target/zenith-catalog.jar app.jar`)
is unchanged. The input file's content checksum — a fingerprint of the jar's actual
bytes — matches the one recorded last build; if even one byte differed, the checksum would
differ and COPY would rerun. And every step before COPY was itself cached. Identical
recipe plus identical ingredients guarantees an identical layer, so skipping is safe.

**Question 2.** You edit only `src/main/resources/application.properties` — not the
Dockerfile, not any Java file. You run `mvn package`, then `docker build` with the same
five-line Dockerfile. For FROM, WORKDIR, COPY: cached or rebuilt?

**Answer.** FROM: cached. WORKDIR: cached. COPY: **rebuilt** — the surprise. The
properties file is packaged *inside* the jar, so `mvn package` produced a jar with new
bytes, and COPY's input checksum changed. The cache watches the input files themselves,
not just whether you touched the Dockerfile.

### Do it yourself (on a fresh laptop)

1. `docker build -t zenith-catalog .` twice in a row. Second run: `CACHED` on `[2/3]` and
   `[3/3]`, tiny context transfer, under a second.
2. Add a `/ping` endpoint to `ZenithCatalogApplication.java`:

   ```java
   @GetMapping("/ping")
   public String ping() {
       return "pong\n";
   }
   ```

3. `mvn -q package -DskipTests`, build again. Expected: WORKDIR `CACHED`; COPY fresh
   (`DONE`); context ~23 MB again.
4. Prove it shipped: `docker run -d -p 8080:8080 --name zc zenith-catalog`, then
   `curl localhost:8080/ping` → `pong`. Clean up: `docker rm -f zc`.
5. **Done when:** before each build, you can call which lines will say `CACHED` — and be
   right.

### Your doubts

None yet.

---

## Lesson 13 — ENTRYPOINT vs CMD: the program and its default arguments

### Definition

Two instructions that together decide what a container runs:

- **ENTRYPOINT** — the program. Fixed. Arguments typed after the image name can never
  replace it.
- **CMD** — the program's **default arguments**. Anything typed after the image name at
  `docker run` replaces CMD entirely.

The container runs ENTRYPOINT + CMD, glued together.

### Why it is needed

One image should serve many situations without rebuilding: default run, different port,
debug shell. The split keeps the image configurable but safe — users change arguments,
never accidentally the program. Also a stock interview and code-review question.

### How it works, step by step

The Dockerfile grew one line:

```dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--server.port=8080"]
```

What the image recorded (`docker inspect`, real):

```text
Entrypoint: [java -jar app.jar]
Cmd:        [--server.port=8080]
```

Composition rules:

1. `docker run image` → ENTRYPOINT + CMD: `java -jar app.jar --server.port=8080`.
2. `docker run image X Y` → X Y replace CMD: `java -jar app.jar X Y`.
3. Only `docker run --entrypoint prog image` replaces the program.
4. Compose's `command:` key is rule 2 — a CMD override (Lesson 7's `dbcheck` used exactly
   this).

### Example — real run on this machine

```text
$ docker run zenith-catalog
... INFO 1 --- Tomcat started on port 8080 ...

$ docker run zenith-catalog --server.port=8081
... Tomcat started on port 8081 ...        ← CMD replaced; Java command unchanged

$ docker run --rm --entrypoint sh zenith-catalog -c 'pwd; ls'
/app
app.jar                                    ← a shell instead of Java
```

(The `INFO 1` in Spring's log is the process id — Java runs as process 1. That number is
the whole next lesson.)

### Important terms

- **ENTRYPOINT** — the program a container runs; replaceable only by `--entrypoint`.
- **CMD** — default arguments; replaced by anything after the image name.
- **`--entrypoint`** — the run flag that swaps the program itself.
- **`command:` (compose)** — the compose spelling of a CMD override.

### Common mistakes

1. **Thinking trailing arguments replace the whole command.** They replace CMD only; the
   program lives safely in ENTRYPOINT.
2. **Putting the whole command in CMD with no ENTRYPOINT.** Then a trailing argument
   replaces everything, and Docker tries to execute `--server.port=8081` as a program:
   `executable file not found`. Program in ENTRYPOINT, defaults in CMD.
3. **Forgetting compose's `command:` is a CMD override.** If a service misbehaves only
   under compose, check it.

### Where it is used

Configurable images everywhere. The `postgres` image (ENTRYPOINT = setup script, CMD =
`postgres`) is why `command: psql ...` could turn it into a one-shot client. Debugging via
`--entrypoint sh`. CI tool images with per-step arguments.

### Link to the task

Subtask 7 executed as written: CMD added, rebuilt, three runs, logs read — 8080, 8081 with
the Java command unchanged, then a shell via the only mechanism that swaps the program.
**Say the rule out loud: ENTRYPOINT is the program, CMD is its default arguments.**

### Summary for revision

- Container command = ENTRYPOINT + CMD.
- Trailing `docker run` args replace CMD, never ENTRYPOINT.
- `--entrypoint` replaces the program; trailing args then become the new CMD.
- Compose `command:` = CMD override.

### Check questions, with answers

**Question 1.** An image is built from:

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/zenith-catalog.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--server.port=8080"]
```

Write the exact full command the container runs for:
(a) `docker run zenith-catalog`
(b) `docker run zenith-catalog --server.port=9000 --debug`
(c) `docker run --entrypoint java zenith-catalog --version`

**Answer.** (a) `java -jar app.jar --server.port=8080` — ENTRYPOINT plus default CMD.
(b) `java -jar app.jar --server.port=9000 --debug` — the trailing words replaced CMD.
(c) `java --version` — the flag replaced the program with `java`, and the trailing
`--version` became the new CMD, i.e. `java`'s argument.

**Question 2.** A Dockerfile has no ENTRYPOINT, only `CMD ["java", "-jar", "app.jar"]`.
Plain `docker run` works. Then `docker run their-image --server.port=8081` fails with
`exec: "--server.port=8081": executable file not found in $PATH`. What happened, and how
does splitting fix it?

**Answer.** The trailing argument replaced the entire CMD — which was the whole command.
Nothing of `java -jar app.jar` remained, so Docker tried to execute the text
`--server.port=8081` as a program and found no such executable. Fix: put the program in
`ENTRYPOINT ["java", "-jar", "app.jar"]` and defaults in `CMD [...]`; trailing arguments
then replace only the arguments.

### Do it yourself (on a fresh laptop)

1. Add `CMD ["--server.port=8080"]` under ENTRYPOINT. Rebuild (mostly `CACHED` — CMD is
   metadata).
2. `docker inspect zenith-catalog --format 'Entrypoint: {{.Config.Entrypoint}}  Cmd: {{.Config.Cmd}}'`.
3. `docker run -d --name run1 zenith-catalog`; `docker logs run1 | grep Tomcat` → port
   **8080**. `docker rm -f run1`.
4. `docker run -d --name run2 zenith-catalog --server.port=8081`; logs → port **8081**.
   `docker rm -f run2`.
5. `docker run --rm -it --entrypoint sh zenith-catalog` → a shell; `pwd` → `/app`; `ls` →
   `app.jar`; `exit`.
6. **Done when:** you can recite the rule and predict all three runs before reading logs.

### Your doubts

None yet.

---

## Lesson 14 — Exec form vs shell form: PID 1 and the ten-second stop

### Definition

Two spellings of ENTRYPOINT:

- **Exec form** — brackets: `ENTRYPOINT ["java", "-jar", "app.jar"]`. Docker starts the
  program directly; it becomes **PID 1**, the first process in the container.
- **Shell form** — bare text: `ENTRYPOINT java -jar app.jar`. Docker wraps the text in
  `/bin/sh -c "..."`; the shell starts first.

`docker stop` sends its "please shut down" signal — **SIGTERM** — to PID 1 and nothing
else. If PID 1 does not act, Docker waits ten seconds, then **SIGKILL** (unappealable)
ends everything.

### Why it is needed

Every deploy, restart, and scale-down begins with SIGTERM to the container. A well-behaved
app finishes in-flight requests and closes connections — a **graceful shutdown**. If the
signal never reaches the app: every stop wastes the full ten seconds, and the app dies
mid-work.

### How it works, step by step

- Exec form: Java is PID 1 → SIGTERM lands on the JVM → Spring runs shutdown hooks →
  clean, fast exit.
- Shell form: `sh` is PID 1, Java its child → `sh` neither dies from SIGTERM nor forwards
  it → Java never hears → ten seconds pass → SIGKILL kills both, cold.
- Extra fact: with a shell-form ENTRYPOINT, `CMD` is silently ignored — the composition
  rule only works in exec form.

### Example — real runs on this machine

**Exec form:**

```text
$ docker exec exec-form ps
PID   USER     TIME  COMMAND
    1 root      0:03 java -jar app.jar --server.port=8080

$ time docker stop exec-form
... 0.121 total
```

**Shell form, lone command — the lucky accident.** Plain `ENTRYPOINT java -jar app.jar`
on this alpine base still showed java at PID 1 and a fast stop. Alpine's shell has an
optimization: given one lone simple command, `sh -c` *replaces itself* with it. Luck —
dependent on the base image's shell and on the command staying trivially simple.

**Shell form with a trailing step — the trap springs:**

```text
ENTRYPOINT java -jar app.jar && echo "app exited"

$ docker exec shell-form ps
PID   USER     TIME  COMMAND
    1 root      0:00 /bin/sh -c java -jar app.jar && echo "app exited"
    7 root      0:03 java -jar app.jar

$ time docker stop shell-form
... 10.112 total
```

`sh` held PID 1; SIGTERM went to it and was swallowed; Java (PID 7) played on, deaf;
Docker's ten seconds expired; SIGKILL ended both. Spring ran no shutdown step. Brackets
restored → stop back to ~0.1 s.

### Important terms

- **PID 1** — the first process in a container; the container lives as long as it does.
- **SIGTERM** — "please shut down"; a program can catch it and clean up.
- **SIGKILL** — immediate, uncatchable kill; sent after the grace period.
- **Grace period** — `docker stop`'s ten-second wait between the two signals.
- **Graceful shutdown** — finishing requests and closing resources on SIGTERM.

### Common mistakes

1. **Shell form because it reads nicer.** Works in dev; in production every deploy is ten
   seconds slower and drops in-flight work. Always brackets.
2. **Misreading the ten seconds as "my app stops slowly".** The app was never asked; the
   ten seconds is Docker's timeout on a deaf PID 1.
3. **Trusting the lucky exec optimization.** True only for that shell, that base, that
   exact lone command. Exec form needs no luck.

### Where it is used

Kubernetes stops pods the same way: SIGTERM, grace period, SIGKILL. Whether your app hears
its last words is decided by this one Dockerfile line. Task 13.1 formalizes it with a
graceful-shutdown check on the production image.

### Link to the task

Subtask 8, as scripted: broke it on purpose, saw `/bin/sh` as PID 1 with Java as child,
timed the ten-second stop, restored brackets, stop back to a tenth of a second.
Checkpoint question 8 answered by experiment.

### Summary for revision

- `docker stop` = SIGTERM to PID 1, ten seconds, SIGKILL.
- Exec form: your program is PID 1 and hears SIGTERM. Shell form: `sh` is PID 1 and
  swallows it.
- Alpine may exec a lone simple command anyway — luck, not design.
- Shell form also disables CMD. Two reasons: always exec form.

### Check questions, with answers

**Question 1.** A container was built with
`ENTRYPOINT java -jar app.jar && echo "app exited"`. Inside it, `ps` shows `/bin/sh -c ...`
as PID 1 and `java -jar app.jar` as PID 7. `time docker stop` reports 10.1 seconds. Tell
the story of those ten seconds: which process received which signal, why Java never
reacted, what ended the container, and what the application lost.

**Answer.** `docker stop` sent SIGTERM to PID 1 only — the shell. The shell neither
exited nor forwarded the signal to its child, so Java never learned a shutdown was
requested and kept serving. After the ten-second grace period, Docker sent SIGKILL, which
cannot be caught, killing shell and Java instantly. The application lost its graceful
shutdown: in-flight requests were dropped and connections closed uncleanly — and every
future deploy would pay the same ten seconds.

**Question 2.** Same app, built with `ENTRYPOINT ["java", "-jar", "app.jar"]`. You run
`docker stop`. Which signal goes where, how long does the stop take, what does Spring get
to do — and when would SIGKILL still be sent?

**Answer.** SIGTERM goes straight to Java, which is PID 1. The JVM triggers Spring's
shutdown hooks: stop accepting new requests, finish current work, close the context and
connection pools, exit. The stop returns in roughly a tenth of a second to a second.
SIGKILL would only follow if the app failed to exit within the ten-second grace period.

### Do it yourself (on a fresh laptop)

1. Exec form: build, `docker run -d --name t zenith-catalog`, `docker exec t ps` (expect
   java at PID 1), `time docker stop t` (expect well under a second). `docker rm t`.
2. Shell form with a trailing step:
   `ENTRYPOINT java -jar app.jar && echo "app exited"`. Rebuild, run, `docker exec t ps`
   (expect `/bin/sh -c ...` at PID 1, java as a child), `time docker stop t` (expect ~10
   seconds). `docker rm t`.
3. Optional nuance: plain `ENTRYPOINT java -jar app.jar` — on alpine, `ps` likely shows
   java at PID 1 anyway (the shell's exec optimization). Say why that is luck.
4. Restore exec form + CMD, rebuild, confirm fast stop.
5. **Done when:** you can tell the PID-1 story without notes.

### Your doubts

None yet.

---

## Lesson 15 — Layer ordering: least to most frequently changed

### Definition

The ordering rule: **put things that change rarely first, things that change often last.**
It follows from the chain rule — the first changed layer forces every later layer to
rebuild, so whatever sits after your frequently-changing files is rebuilt on every edit.

New instruction: **`RUN`** executes a command *inside the image during the build*
(installs, downloads, compiles); its filesystem changes become a layer. `RUN` steps are
usually the expensive ones — their position is what this lesson is about.

### Why it is needed

This rule alone is the difference between a four-second rebuild and a four-minute one,
paid on every code change, locally and in CI.

### How it works, step by step

1. The cache rebuilds the first layer whose instruction or inputs changed — and then
   everything after it, unconditionally.
2. Code changes many times a day; the dependency list weekly; the base image monthly.
3. Therefore: base first, dependencies next, code last. Edits then invalidate only the
   cheap final layers.

### Example — real experiment on this machine

Two files, identical content, different order. The slow step is a stand-in: `sleep 5`
playing a 200 MB dependency download. (`docker build -f <name>` picks a recipe not named
plain `Dockerfile`.)

```dockerfile
# Dockerfile.bad — code BEFORE the slow step
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/zenith-catalog.jar app.jar
RUN sleep 5 && echo "pretend: 200MB of tools downloaded" > /tools.txt
ENTRYPOINT ["java", "-jar", "app.jar"]

# Dockerfile.good — slow step BEFORE the code
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN sleep 5 && echo "pretend: 200MB of tools downloaded" > /tools.txt
COPY target/zenith-catalog.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Both built once (each paid its 5 seconds). Then **one source change** (`/ping` now returns
`pong v2`), `mvn package`, and both rebuilt:

**Bad order:**

```text
#6 [2/4] WORKDIR /app
#6 CACHED
#7 [3/4] COPY target/zenith-catalog.jar app.jar
#7 DONE 0.0s                 ← jar changed, fair
#8 [4/4] RUN sleep 5 && echo "pretend: ..." > /tools.txt
#8 DONE 5.1s                 ← re-ran though NOTHING about it changed
total: 6.3 seconds
```

**Good order, same change:**

```text
#6 [2/4] WORKDIR /app
#6 CACHED
#7 [3/4] RUN sleep 5 && echo "pretend: ..." > /tools.txt
#7 CACHED                    ← the slow step survived the edit
#8 [4/4] COPY target/zenith-catalog.jar app.jar
#8 DONE 0.0s
total: 1.0 second
```

Same edit, six times faster — purely from order. Scale the 5-second stand-in to a
three-minute Maven download: that is four seconds versus four minutes.

**The rule, out loud: order instructions from least to most frequently changed.**

### Important terms

- **`RUN`** — execute a command during the build; the changes become a layer.
- **Chain rule** — first rebuilt layer forces all later layers to rebuild.
- **`docker build -f <file>`** — build from a recipe with a non-default name.

### Common mistakes

1. **`COPY . .` first, setup after.** Every edit to anything invalidates everything.
2. **Reading "re-ran" as a broken cache.** The cache obeyed the chain rule; the file's
   order was the bug.
3. **Treating order as style.** It is the main lever on build time — and the interview
   question "why `COPY pom.xml` before `COPY src`?" in disguise.

### Where it is used

Every Dockerfile in every repository; CI time and cost. Reviewers reject misordered
files.

### Link to the task

Subtask 9 done (`[4/4] RUN ... DONE 5.1s` after a one-character change is the smoking
gun) and subtask 11 rehearsed. The multi-stage lesson applies it with real Maven:
`COPY pom.xml` + dependency download first, `COPY src` after.

### Summary for revision

- First changed layer rebuilds; everything after follows. Order decides the bill.
- Base → dependencies → code. Least to most frequently changed.
- Real numbers from one source edit: bad order 6.3 s, good order 1.0 s.

### Check questions, with answers

**Question 1.** Two Dockerfiles for one app. File A: `COPY app.py /app/` then
`RUN pip install -r requirements.txt` (slow download). File B: the same two steps in the
opposite order. `app.py` changes many times a day; `requirements.txt` about monthly
(assume the install's own inputs are unchanged day to day). Which gives fast daily
rebuilds? Narrate the cache on an `app.py`-only edit for both.

**Answer.** File B. In file A, the edit changes COPY's input, COPY rebuilds, and the
chain rule forces the slow `pip install` after it to rerun — the full download, daily,
for nothing. In file B the install sits above the edit: its text and inputs are
unchanged and everything before it is cached, so it prints `CACHED`; only the cheap COPY
reruns. Same edit, seconds instead of minutes.

**Question 2.** A Dockerfile reads, in order: `FROM maven`, `WORKDIR /src`,
`COPY pom.xml .`, `RUN mvn dependency:go-offline` (downloads all dependencies),
`COPY src ./src`, `RUN mvn package`. You edit one Java file inside `src/` and rebuild.
For each instruction: `CACHED` or rebuilt? Which expensive step did the ordering save?

**Answer.** `FROM`: cached. `WORKDIR`: cached. `COPY pom.xml`: cached — the pom did not
change. `RUN mvn dependency:go-offline`: cached — same instruction, same pom, all prior
steps cached. `COPY src`: rebuilt — a file in it changed. `RUN mvn package`: rebuilt — it
sits after the first rebuilt layer (and its inputs changed). Saved: the dependency
download, the slowest step, which survives hundreds of source edits untouched.

### Do it yourself (on a fresh laptop)

1. In `zenith-catalog`, create `Dockerfile.bad` and `Dockerfile.good` exactly as above.
2. Build both once: `docker build -f Dockerfile.bad -t zc-bad .` and
   `docker build -f Dockerfile.good -t zc-good .`.
3. Change the ping reply from `"pong\n"` to `"pong v2\n"` in
   `ZenithCatalogApplication.java`; run `mvn -q package -DskipTests`. (From here on,
   `/ping` answers `pong v2`.)
4. `time docker build -f Dockerfile.bad -t zc-bad .` — expect RUN `DONE 5.x s`, total ~6
   s. Then `time docker build -f Dockerfile.good -t zc-good .` — expect RUN `CACHED`,
   total ~1 s.
5. **Done when:** you predicted each step's verdict beforehand and can say the rule from
   memory.

### Your doubts

None yet.

---

## Lesson 16 — Multi-stage builds: build in one image, ship another

### Definition

A **multi-stage build** is one Dockerfile with **more than one `FROM` line**. Each `FROM`
starts a new, separate image called a **stage**; `AS <name>` names a stage. A later stage
copies files out of an earlier one with `COPY --from=<name>`. Only the **last** stage
becomes the image — earlier stages are scaffolding, kept only as cache.

Ours: a **build stage** (Maven + JDK) compiles the jar; a **run stage** (small JRE)
receives the jar and nothing else.

### Why it is needed

The single-stage file had a hidden dependency on the laptop: `COPY target/...jar` only
works after a host `mvn package`, so building the image needs Java and Maven installed.
The naive fix — build inside a Maven image and ship that — puts an 847 MB toolchain in
production. Multi-stage gives both halves: anyone with Docker alone can build it (pure
source in, image out), and production gets only the slim runtime.

### How it works, step by step

1. Stage one builds as a normal image — layers, cache, all the usual rules.
2. At the next `FROM`, Docker starts a **fresh image from scratch**; nothing from stage
   one is in it.
3. `COPY --from=build <path>` redirects the *source* of the copy: it reads stage one's
   finished filesystem instead of the build context.
4. The final image is the last stage only. Stage one is not shipped, not pushed.
5. The cache works per stage — so the ordering rule (pom + download above, source below)
   is applied inside the build stage.

### The file (this replaced `Dockerfile`)

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- `mvn dependency:go-offline` downloads **every** dependency and plugin using **only the
  pom** — the slow step, placed above the code on purpose.
- `-DskipTests` skips tests during `package` (CI runs them elsewhere; keeps the build
  fast).
- Output quirk: the named stage prints `[build 1/6]`…, the unnamed final stage prints
  `[stage-1 1/3]` — stages are numbered from 0, so the second one is "stage-1".

### Example — two real builds on this machine

First build (old image kept as `zenith-catalog:single-stage` beforehand):

```text
#11 [build 4/6] RUN mvn -q dependency:go-offline
#11 DONE 25.9s                ← every dependency, downloaded inside the build
#13 [build 6/6] RUN mvn -q package -DskipTests
#13 DONE 3.0s
total: 30.3 seconds
```

26 seconds of downloading even though the laptop has these jars — the build stage is a
sealed container and cannot see `~/.m2`. First build pays full price.

Then **one source string changed** (`pong v2` → `pong v3`), **no host `mvn` run at all**,
and `docker build` again:

```text
#9  [build 3/6] COPY pom.xml .
#9  CACHED
#10 [build 4/6] RUN mvn -q dependency:go-offline
#10 CACHED                    ← no re-download: the pom didn't change
#11 [build 5/6] COPY src ./src
#11 DONE 0.0s                 ← first changed layer
#12 [build 6/6] RUN mvn -q package -DskipTests
#12 DONE 2.8s                 ← compile only, already offline
total: 4.0 seconds
```

30 s → 4 s; dependencies downloaded exactly once. Proof the jar came from inside Docker:
the host's `target/` jar still answered `pong v2` (never rebuilt there), but:

```text
$ curl localhost:8080/ping
pong v3
```

### Important terms

- **Stage** — one `FROM`-section of a Dockerfile; a separate image during the build.
- **`AS <name>`** — names a stage.
- **`COPY --from=<stage>`** — copy from that stage's filesystem instead of the context.
- **`dependency:go-offline`** — Maven goal: download all dependencies using only the pom.
- **Build stage / run stage** — compile with the big toolchain; ship only the runtime.

### Common mistakes

1. **`COPY . .` before `go-offline`** — every source edit re-downloads all dependencies;
   the badly-ordered file at full price.
2. **Forgetting `--from=build`** — COPY silently reads the laptop's `target/` and ships a
   stale host-built jar. The v2/v3 check is how you catch it.
3. **Shipping the build stage** — one `FROM maven` image that builds *and* runs: works,
   but puts 850+ MB of compiler in production.
4. **Expecting the host's `~/.m2` inside the build.** The stage is sealed; first build
   always downloads — which is why keeping the `go-offline` layer cached matters.

### Where it is used

The standard pattern for compiled languages everywhere — Java, Go, Rust, C#, and
frontend (node builds, nginx serves). CI runs exactly one command: `docker build`.

### Link to the task

Subtask 10 done (multi-stage written and built). Subtask 11 answer, out loud: *pom
changes rarely, source changes constantly; pom + download above source means a source
edit invalidates only copy-and-compile, and the download stays cached.* Done-when item
"source-only change rebuilds without re-downloading dependencies" — proven above.

### Summary for revision

- Multiple `FROM`s = stages; last stage is the image; `COPY --from` moves artifacts.
- Build stage: pom → go-offline → src → package. Run stage: JRE + jar only.
- Real numbers: first build 30.3 s; source-only rebuild 4.0 s with `go-offline` CACHED.
- The stage cannot see the host's Maven cache; the layer cache replaces it.

### Check questions, with answers

**Question 1.** A teammate edits `pom.xml` to add one dependency and rebuilds. Walk the
six build-stage steps — which are CACHED, which rebuild, and is the re-download wasteful?

**Answer.** `FROM`: cached. `WORKDIR`: cached. `COPY pom.xml`: **rebuilds** — its input
changed. `RUN go-offline`: **rebuilds** — it sits after the first changed layer, and its
real input (the pom) changed. `COPY src` and `RUN package`: rebuild (chain rule; the copy
is quick, the compile pays normal price). The re-download is **correct, not wasteful**:
the dependency list genuinely changed, so the new jar must be fetched. The design goal
is exactly this — downloads rerun only when the pom changes, which is rare.

**Question 2.** You delete `--from=build`, leaving `COPY /src/target/*.jar app.jar`, and
the build still succeeds on your laptop. What jar is in the image, when does it bite,
and on whose machine does the build fail outright?

**Answer.** Without `--from`, COPY reads the **build context** — the laptop's folder — so
the image gets the stale jar from the last *host* `mvn package`, not the one stage one
just compiled. It bites when you edit source, rebuild the image, and the change does not
appear: the container runs old code while the build says success. On a teammate's or CI
machine with a fresh clone (no `target/` folder), the COPY finds nothing and the build
**fails** — which is at least honest. The silent-staleness on your machine is the
dangerous half.

### Do it yourself (on a fresh laptop)

1. Keep the old image for comparison: `docker tag zenith-catalog zenith-catalog:single-stage`.
2. Replace `zenith-catalog/Dockerfile` with the multi-stage file above.
3. `time docker build -t zenith-catalog .` — first build downloads everything inside;
   expect the `go-offline` step to dominate (seconds to minutes, network-dependent).
4. Edit the ping reply `"pong v2\n"` → `"pong v3\n"`. Do **not** run `mvn` on the host.
5. `time docker build -t zenith-catalog .` — expect `COPY pom.xml` CACHED,
   `RUN mvn -q dependency:go-offline` **CACHED**, only `COPY src` + `package` re-run.
6. `docker run -d --name zc-ms -p 8080:8080 zenith-catalog`, then
   `curl localhost:8080/ping` → `pong v3`; clean up with `docker rm -f zc-ms`.
7. **Done when:** a source-only rebuild shows `go-offline` CACHED — no re-download.

### Your doubts

None yet.

---

## Lesson 17 — The finale: what you ship, and the stack assembled

### Definition

Two closing moves. The **size comparison**: the images we built, side by side, showing
what multi-stage actually saved. The **assembly**: the app joins Postgres and Redis in
`compose.yml` as a third service, and every done-when item of the task is verified with
real commands.

### Why it is needed

Numbers end arguments — "multi-stage is smaller" is a slogan, *730 MB smaller* is a fact
you can defend in review. And the assembled stack is the task's whole point: one
`docker compose up -d` brings up a database, a cache, and your own application,
correctly ordered, on one network.

### The size showdown

Real output from this machine (`docker images`). `zc-naive` was built from a one-stage
file that compiles *and* runs in the Maven image — the thing the task warns about:

```text
zc-naive:latest                1.06GB   ← builds AND runs in the Maven image
maven:3.9-eclipse-temurin-21   847MB    ← the build toolchain alone
zenith-catalog:latest          330MB    ← our multi-stage image
eclipse-temurin:21-jre-alpine  287MB    ← the run-stage base
```

- **Naive = 1.06 GB**: the 847 MB toolchain, plus every downloaded dependency in Maven's
  cache folder, plus source, plus `target/` debris — all shipped, none used at runtime.
  Multi-stage = the 287 MB JRE plus a ~23 MB jar: **330 MB**. Difference: 730 MB of dead
  weight.
- **The naive image built in 3.6 s** — its first six instructions are identical to our
  build stage, so the layer cache served all of them. Stages and files share one cache.
  Fast to build, bloated to ship: the argument is contents, not speed.
- The sentence to memorize: *the multi-stage image ships no Maven and no compiler.*

### The final compose.yml

The `dbcheck` one-shot tester retires; the real app takes its seat:

```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_PASSWORD: dev
      POSTGRES_DB: zenith
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 2s
      timeout: 3s
      retries: 15

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  app:
    image: zenith-catalog
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/zenith
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  pgdata:
```

The `app` service in one breath: `image: zenith-catalog` uses the local multi-stage image
(nothing pulled); the JDBC URL says **`postgres`** — the service name — because the app
talks to the database across the project network, where Compose's DNS turns service
names into container addresses (`localhost` inside the app container would mean the app
container itself); `condition: service_healthy` holds the app back until the healthcheck
says Postgres is *accepting connections*, not merely started.

### Example — every done-when item, verified live

One command up, choreography visible:

```text
$ docker compose up -d
 Container zenith-stack-redis-1     Started
 Container zenith-stack-postgres-1  Started
 Container zenith-stack-postgres-1  Waiting      ← healthcheck gate
 Container zenith-stack-postgres-1  Healthy
 Container zenith-stack-app-1       Starting     ← only after Healthy
 Container zenith-stack-app-1       Started
```

Postgres healthy, Redis answering *(done-when 1)*:

```text
$ docker compose ps
NAME                      ...  STATUS
zenith-stack-app-1        ...  Up 2 seconds
zenith-stack-postgres-1   ...  Up 5 seconds (healthy)
zenith-stack-redis-1      ...  Up 5 seconds

$ docker exec zenith-stack-redis-1 redis-cli ping
PONG
```

The app connects to Postgres by service name *(done-when 2)*:

```text
$ curl localhost:8080/db
db says: 2026-11-17 18:41:35.160551+00
```

That timestamp is Postgres's own clock, fetched by the app over the compose network
through `jdbc:postgresql://postgres:5432/zenith`.

Multi-stage image builds; source-only change re-downloads nothing *(done-when 3 and 4)* —
proven in the multi-stage lesson: first build 30.3 s with `go-offline DONE 25.9s`,
rebuild after a one-string edit 4.0 s with `go-offline CACHED`.

Exec-form stop is instant *(done-when 5)*:

```text
$ time docker stop zenith-stack-app-1
docker stop zenith-stack-app-1  ... 0.160 total
```

0.16 seconds: java is PID 1 (exec-form ENTRYPOINT), SIGTERM lands on it, Spring Boot
shuts down cleanly. The shell-form experiment took 10.1 s for the same stop.

Closed with plain `docker compose down` — containers and network gone, `pgdata` kept.

### Important terms

- **Naive one-stage image** — build toolchain and app in one image; works, ships ~3×
  the weight.
- **`image: <local-tag>`** — Compose uses a locally built image by that tag; no pull.
- **Done-when checklist** — the task's five verifiable claims; each one has a command.

### Common mistakes

1. **Defending the naive image with "it works".** It does — and it ships a compiler, a
   build tool, and a dependency cache: bigger pulls, slower deploys, more to patch.
2. **`localhost` creeping back into the JDBC URL.** Works on the laptop thanks to port
   mapping, dies in compose — inside a container, `localhost` is that container.
3. **Plain `depends_on` without the condition.** Order without readiness: the app may
   boot while Postgres is still initializing and fail its first queries.
4. **`down -v` out of habit.** `-v` wipes `pgdata`. Plain `down` daily; `-v` only for a
   deliberate factory reset.

### Where it is used

This trio — compose file, healthcheck-gated dependencies, multi-stage app image — is the
local-development standard in most backend teams, and the same Dockerfile is what CI
builds and production runs.

### Link to the task

Subtask 12 done (sizes compared, 1.06 GB vs 330 MB, and why). Final wiring done — all
five done-when items verified above. **Subtask 13 is yours alone:** ask an AI *"explain
Docker layer caching with a badly ordered and a well ordered Dockerfile"* and grade its
answer against what you proved here.

### Summary for revision

- Naive build-and-run image 1.06 GB; multi-stage 330 MB; the difference is toolchain,
  dependency cache, and source — dead weight at runtime.
- App service: local image by tag, service-name JDBC URL, healthcheck-gated
  `depends_on`.
- Done-when, all green: healthy Postgres + Redis PONG; `/db` answers via service name;
  multi-stage builds; source-only rebuild keeps `go-offline` CACHED; exec-form stop in
  0.16 s.

### Check questions, with answers

**Question 1.** The naive one-stage image weighs 1.06 GB; the multi-stage image 330 MB.
Name at least three concrete things inside the naive image that the multi-stage image
does not ship, and say why "but it runs fine" is not a defense.

**Answer.** Inside the naive image and absent from the multi-stage one: (1) Maven itself,
the build tool; (2) the full JDK with the compiler, where the run stage carries only the
JRE; (3) Maven's dependency cache — every jar `go-offline` downloaded; (4) the source
code; (5) `target/` build debris. "It runs fine" ignores where the cost lands: every
pull and deploy moves 3× the bytes, every node stores them, and the extra software is
attack surface and patch duty — a compiler in production helps no one but an intruder.
The multi-stage image runs *exactly as fine* at a third of the weight.

**Question 2.** The app's database URL is `jdbc:postgresql://postgres:5432/zenith` and
its `depends_on` carries `condition: service_healthy`. What would go wrong with
`localhost` in the URL, and what does the `condition` add over plain `depends_on`?

**Answer.** Inside the app container, `localhost` means *that container itself* — no
Postgres lives there, so the connection is refused; the Mac-side port mapping does not
exist inside the network. The service name `postgres` is resolved by the project
network's DNS to the postgres container's address — that is the rule: service name
between containers, `localhost` only from the Mac. Plain `depends_on` orders only
*starting*: the app launches once the postgres container exists, which can be seconds
before the database inside accepts connections — first queries fail. With
`condition: service_healthy`, Compose waits for the healthcheck (`pg_isready`) to pass
before starting the app at all — visible in the up output as `Waiting → Healthy →
Starting`.

### Do it yourself (on a fresh laptop)

1. In `zenith-catalog`, create `Dockerfile.naive` — the multi-stage build stage plus
   `ENTRYPOINT ["java", "-jar", "target/zenith-catalog.jar"]`, all under the single
   `FROM maven:3.9-eclipse-temurin-21` (no second FROM, no `AS build`, no `--from`).
2. `docker build -f Dockerfile.naive -t zc-naive .` — expect mostly CACHED, a few
   seconds.
3. `docker images` — expect `zc-naive` ≈ 1 GB, `zenith-catalog` ≈ 330 MB.
4. Replace `zenith-stack/compose.yml` with the final version above (app replaces
   dbcheck).
5. `docker compose up -d` — expect `postgres Waiting → Healthy → app Starting`.
6. Verify, in order: `docker compose ps` (postgres `(healthy)`);
   `docker exec zenith-stack-redis-1 redis-cli ping` → `PONG`;
   `curl localhost:8080/db` → `db says: <timestamp>`;
   `time docker stop zenith-stack-app-1` → well under a second.
7. `docker compose down` (no `-v` — keep the data).
8. **Done when:** all five checks pass and you can say, for each, which lesson proved it.

### Your doubts

None yet.

---

## Task F7.9 — closing state

- **All subtasks done** except 13 (ask an AI about layer caching — you do that yourself
  and grade the answer with what you proved here).
- **Done-when checklist:** all five verified with real output (healthy Postgres + Redis
  PONG; app reaches Postgres via service name; multi-stage builds; source-only rebuild
  keeps `go-offline` CACHED; exec-form stop in 0.16 s).
- **Files that remain as reference:** `zenith-stack/compose.yml` (final stack),
  `zenith-catalog/Dockerfile` (multi-stage), `Dockerfile.naive`, `Dockerfile.bad`,
  `Dockerfile.good` (teaching artifacts), `hello-compose/compose.yml` (first steps).
- **Local state:** stack is down; the `zenith-stack_pgdata` volume is kept; images
  `zenith-catalog:latest` (multi-stage), `zenith-catalog:single-stage`, `zc-naive`,
  `zc-bad`, `zc-good` remain for inspection. Free space anytime with
  `docker image prune` / `docker rmi zc-naive zc-bad zc-good`.

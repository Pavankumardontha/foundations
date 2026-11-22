# Task 0.1 — Local infrastructure in one command

Notes for Task 0.1. One lesson per section. Each lesson ends with its understanding-check
questions and full answers.

The task in one line: install the toolchain (Steps 1–2), build one Compose file that runs
Postgres, Redis, Kafka, Schema Registry, MinIO and Mailpit (Steps 3–9), and wrap every daily
operation in a Makefile proven by a from-scratch setup document (Steps 10–12). Done when
`make reset && make up` gives a healthy stack in under 90 seconds.

---

## Lesson map

| # | Lesson | Serves |
|---|--------|--------|
| 1 | Java and Maven through SDKMAN, pinned with `.sdkmanrc` | Step 1 |
| 2 | The container runtime (a hidden Linux VM) and the toolbox | Step 2 |
| 3 | Container vs VM (the kernel), and Postgres starts the infra file | Step 3 |
| 4 | Redis joins the file; `up -d` means converge, not restart | Step 3 |
| 5 | Kafka in KRaft mode: metadata, listeners, one container | Step 3 |
| 6 | Schema Registry: the shape contract, stored inside Kafka | Step 3 |
| 7 | MinIO (object storage), image sourcing in 2026, and the run-once bootstrap | Step 3 |
| 8 | Mailpit (fake SMTP), and the six-service milestone | Step 3 |
| 9 | Named volumes: lose data on purpose, then make it survive | Step 4 |
| 10 | Healthchecks, the named network `infra-net`, restart policies | Step 4 |
| 11 | `depends_on` + `condition: service_healthy`, and `up -d --wait` | Step 5 |
| 12 | Break it on purpose: races, scars, and what "flaky" means | Step 6 |
| 13 | Bind mounts: config you edit, delivered into the container | Step 7 |
| 14 | `.env`: credentials out of the YAML — convenience, not secrecy | Step 8 |
| 15 | Profiles: start only the slice of the stack you need | Step 9 |
| 16 | The Makefile: one word per intention | Step 10 |
| 17 | The Makefile grows with the project — the menu is a contract | Step 11 |
| 18 | Prove the document, not the memory — and the task checkpoint | Step 12 |

(The map grows as lessons are added.)

---

## Lesson 1 — Step 1: Install Java and Maven through SDKMAN

### Definition

A **version manager** is a tool that keeps several versions of a program installed side by
side and switches between them. **SDKMAN** is the standard version manager for Java-world
tools (Java itself, Maven, Gradle, and more). A **`.sdkmanrc`** file at the repo root records
the exact Java version the project builds with, so the version is a fact committed with the
code, not an accident of whichever machine you are on.

### Why it is needed

Before this step, this machine's Java came from Homebrew:

```text
openjdk version "21.0.11" 2026-04-21
OpenJDK Runtime Environment Homebrew (build 21.0.11)
```

That version is whatever Homebrew had on install day. Three problems:

- **Drift over time.** A future `brew upgrade` can silently move the machine to a newer Java.
  The project builds differently on Tuesday than Monday, and nothing recorded why.
- **Drift between machines.** The second laptop installed Java on a different day, so it has a
  different version. "Works on my machine" starts in the toolchain, not in the code.
- **Nothing written down.** The version the project needs lives nowhere in the project.

A version manager plus a pin file fixes all three: every machine reads the same committed
`.sdkmanrc` and gets byte-for-byte the same Java.

### How it works, step by step

1. SDKMAN installs every version into its own folder under `~/.sdkman/candidates/`.
   A full JDK for each version, nothing shared, nothing overwritten.
2. One **symlink** (a shortcut file that points at another folder) called `current` points at
   the chosen version. Real layout on this machine:

   ```text
   ~/.sdkman/candidates/java/
     21.0.13.1+2-tem/            <- a full JDK lives in here
     current -> 21.0.13.1+2-tem  <- the shortcut
   ```

3. **PATH** is the ordered list of folders the shell searches when you type a command; the
   first match wins. SDKMAN puts `~/.sdkman/candidates/java/current/bin` near the front, so
   `java` now resolves there. The old Homebrew Java is still on disk — it just loses the race.
4. **JAVA_HOME** (the variable tools like Maven use to find Java) is pointed at the same
   `current` shortcut, so the terminal's `java` and Maven's Java always agree:

   ```text
   JAVA_HOME=/Users/pavankumar/.sdkman/candidates/java/current
   ```

5. "Switching versions" = repointing the `current` shortcut. No copying, instant, even though
   a JDK is hundreds of megabytes.
6. To make `sdk` exist in every new terminal, the installer appended two lines at the end of
   `~/.zshrc` (the script zsh runs for every new terminal — which is why already-open
   terminals do not see new installs until you open a fresh one):

   ```bash
   export SDKMAN_DIR="$HOME/.sdkman"
   [[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"
   ```

7. `sdk env` reads `.sdkmanrc` in the current folder and switches that shell to the pinned
   version. `sdk env install` reads the same file and downloads the pinned version if missing.

### Example — real commands, real output from this machine

**Step 1 — install SDKMAN. The first attempt actually failed:**

```bash
curl -s "https://get.sdkman.io" | bash
```

```text
SDKMAN requires Bash 4 or higher, but you are running Bash 3.2.57(1)-release.

 Please upgrade Bash using your system's package manager.
 On macOS, you can install a modern Bash with Homebrew:

    $ brew install bash
```

Decoded: macOS ships a bash from 2007 (3.2.57) and Apple never updates it, for licensing
reasons. The login shell here is zsh, but SDKMAN's own scripts are bash and need a modern one.
The fix is exactly what the error says:

```bash
brew install bash
bash --version | head -1
```

```text
GNU bash, version 5.3.20(1)-release (aarch64-apple-darwin25.6.0)
```

This adds Bash 5 in Homebrew's folder *alongside* the system one. Nothing system-level is
replaced; the login shell stays zsh. Retry of the installer:

```text
All done!

Please open a new terminal, or run the following in the existing one:

    source "/Users/pavankumar/.sdkman/bin/sdkman-init.sh"
```

**Step 2 — new terminal, confirm `sdk` exists:**

```bash
sdk version
```

```text
SDKMAN!
core:   5.24.0
native: 0.8.0 (macos aarch64)
```

**Step 3 — find the newest Temurin 21.** Temurin (identifier suffix `-tem`) is the Eclipse
Foundation's free build of open-source Java — the safe default everyone uses. 21 and not 27,
because 21 is an **LTS** (long-term support) release, patched for years; the in-between
releases live only six months, and real projects sit on LTS.

```bash
sdk list java
```

```text
 Vendor         | Use | Version            | Identifier
--------------------------------------------------------------------------------
 Temurin        |     | 27.0.0.0+35        | 27.0.0.0+35-tem
                |     | 26.0.2.1+1         | 26.0.2.1+1-tem
                |     | 25.0.5.1+2         | 25.0.5.1+2-tem
                |     | 21.0.13.1+2        | 21.0.13.1+2-tem    <- newest 21.x
```

**Step 4 — install Java and Maven:**

```bash
sdk install java 21.0.13.1+2-tem
```

```text
Downloading: java 21.0.13.1+2-tem
In progress...
Done installing!
Setting java 21.0.13.1+2-tem as default.
```

```bash
sdk install maven
```

```text
Downloading: maven 3.10.1
Done installing!
Setting maven 3.10.1 as default.
```

**Step 5 — verify both, and verify they agree:**

```bash
which java
java -version
```

```text
/Users/pavankumar/.sdkman/candidates/java/current/bin/java
openjdk version "21.0.13.1" 2026-11-17 LTS
OpenJDK Runtime Environment Temurin-21.0.13.1+2 (build 21.0.13.1+2-LTS)
```

```bash
mvn -version
```

```text
Apache Maven 3.10.1 (3afd8084b5629f80b97750906a719879ed4cd283)
Maven home: /Users/pavankumar/.sdkman/candidates/maven/current
Java version: 21.0.13.1, vendor: Eclipse Adoptium, runtime: /Users/pavankumar/.sdkman/candidates/java/21.0.13.1+2-tem
```

The line that matters is Maven's `Java version:` line — `vendor: Eclipse Adoptium` with a
`runtime:` inside `~/.sdkman` proves Maven runs on the pinned Java, not on some other Java it
found by itself. (Maven locates Java through `JAVA_HOME`, so in theory the terminal's `java`
and Maven's Java can differ — this line is how you catch that.)

**Step 6 — pin the version in the repo.** File `.sdkmanrc` at the repo root:

```text
# Pin the exact Java this project builds with. Activate with: sdk env
java=21.0.13.1+2-tem
```

```bash
sdk env
```

```text
Using java version 21.0.13.1+2-tem in this shell.
```

**Failures worth recognising** (both produced for real):

```bash
sdk install java 21.0.99-tem        # mistyped version
```

```text
Stop! java 21.0.99-tem is not available. Possible causes:
 * 21.0.99-tem is an invalid version
 * java binaries are incompatible with your platform
```

```bash
sdk env                             # .sdkmanrc pins a version not installed here
```

```text
Stop! java 17.0.21.0+9-tem is not installed.
Run 'sdk env install' to install it.
```

That second error is a feature: on a new machine, `sdk env install` reads the repo's pin and
downloads exactly the right version. The repo tells the machine what it needs.

### Important terms

| Term | Meaning |
|---|---|
| Version manager | Tool that keeps several versions installed side by side and switches between them. |
| SDKMAN | The version manager for Java-world tools. Everything lives under `~/.sdkman`. |
| Candidate | SDKMAN's word for a tool it manages (`java`, `maven`, ...). |
| Symlink | A shortcut file that points at another folder. `current -> 21.0.13.1+2-tem`. |
| PATH | Ordered list of folders the shell searches for commands; first match wins. |
| JAVA_HOME | Environment variable tools like Maven use to locate the JDK. |
| Temurin (`-tem`) | Eclipse Foundation's free build of OpenJDK; the default choice. |
| LTS | Long-term support release (17, 21, 25...). Patched for years; projects sit on these. |
| `.sdkmanrc` | Per-repo pin file: `java=21.0.13.1+2-tem`. Committed with the code. |
| `sdk env` | Reads `.sdkmanrc`, switches this shell to the pinned version. |
| `sdk env install` | Reads `.sdkmanrc`, installs the pinned version if missing. |
| `sdk list java` | Catalog of installable Javas. Identifier column is what you install. |

### Common mistakes

- **Running `sdk` in the same terminal that ran the installer.** `command not found: sdk` —
  the installer edited `~/.zshrc`, which only new terminals read. Open a fresh one.
- **Installing "java 21" loosely on each machine.** Two machines then hold two different 21.x
  builds. The pin exists so both run the identical build; on the second machine prefer
  `sdk env install` over picking from the list by eye.
- **Checking only `java -version` and never `mvn -version`.** Maven follows `JAVA_HOME`; the
  terminal follows PATH. The `Java version:` line in `mvn -version` is the proof they agree.
- **Expecting `.sdkmanrc` to apply by itself.** Plain SDKMAN applies it only when you run
  `sdk env` in that folder. (There is an opt-in config, `sdkman_auto_env=true`, that applies
  it on every `cd`.)

### Where it is used

- This repo: `.sdkmanrc` at the root pins `21.0.13.1+2-tem` for every machine that clones it.
- Every later phase: the services, tests and builds of the whole project compile on this
  exact Java.
- Real teams: version pins in the repo (same idea as `.nvmrc` for Node or `.python-version`
  for Python) are how a build stays identical across laptops and CI.

### Link to the task

- Step 1's "you know it worked when", word for word: `java -version` prints a 21.x version and
  `mvn -version` prints a Maven version that says it is running on that same Java. Both shown
  above with real output.
- The pin also feeds Step 12: `docs/local-setup.md` can say "run `sdk env install`" instead of
  "go find the right Java" — the document stays short because the repo carries the answer.

### Do it yourself

On the second laptop (assume nothing installed), in order:

1. **Homebrew** — skip if `brew --version` already answers:

   ```bash
   /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
   ```

   Follow the prompts; at the end it prints two `eval` lines — run them.

2. **Modern bash** (macOS's own bash is 3.2 from 2007; SDKMAN needs 4+):

   ```bash
   brew install bash
   bash --version   # expect: GNU bash, version 5.x
   ```

3. **SDKMAN:**

   ```bash
   curl -s "https://get.sdkman.io" | bash
   ```

   Expect ASCII art ending in `All done!`.
   Failure mode: `SDKMAN requires Bash 4 or higher` → step 2 was skipped.

4. **Open a brand-new terminal**, then:

   ```bash
   sdk version    # expect: SDKMAN! core: 5.24.0 (or newer)
   ```

   Failure mode: `zsh: command not found: sdk` → you are still in the old terminal.

5. **Install the pinned Java** (same version as this repo's `.sdkmanrc`, so the machines
   match):

   ```bash
   sdk install java 21.0.13.1+2-tem
   ```

   Answer `Y` to `set as default?`. Expect `Done installing!`.
   Failure mode: `Stop! ... is not available` → run `sdk list java` (press `q` to quit the
   list) and use the newest 21.x `-tem` identifier it shows.

6. **Install Maven:**

   ```bash
   sdk install maven    # expect: Done installing!
   ```

7. **Verify, in a new terminal:**

   ```bash
   java -version   # expect: Temurin-21.0.13.1+2
   mvn -version    # expect Java version line: vendor: Eclipse Adoptium,
                   #        runtime: /Users/<you>/.sdkman/candidates/java/21.0.13.1+2-tem
   ```

8. **Pin it.** At the project repo root, create `.sdkmanrc` containing:

   ```text
   java=21.0.13.1+2-tem
   ```

   Then, inside that folder:

   ```bash
   sdk env         # expect: Using java version 21.0.13.1+2-tem in this shell.
   ```

   Failure mode: `Stop! ... is not installed. Run 'sdk env install'` → run exactly that.

**Done when:** `java -version` prints Temurin 21.0.13.1, `mvn -version` shows Maven running on
that same Java, and `sdk env` at the repo root answers `Using java version 21.0.13.1+2-tem`.

### Summary for revision

- Homebrew-installed Java = whatever version that day; drifts over time and across machines.
- SDKMAN: one folder per version under `~/.sdkman/candidates/`, a `current` symlink chooses,
  PATH makes the choice win, JAVA_HOME keeps Maven on the same Java.
- Switching = repointing a shortcut. Instant.
- Temurin = free Eclipse build of OpenJDK. 21 because LTS.
- `.sdkmanrc` at repo root pins the exact build; `sdk env` applies it; `sdk env install`
  fetches it on a new machine. The version is a committed fact, not a machine accident.
- macOS bash is from 2007; SDKMAN needs `brew install bash` first.

### Check questions, with answers

**Question 1.** A teammate clones your repo onto a laptop whose default Java is 25. What two
commands give them the exact Java this project needs, and where does the second one learn
which version to fetch?

**Answer.** Inside the cloned repo: `sdk env install` (downloads and installs the pinned
version if missing), then `sdk env` (switches that shell to it). Both read the version from
the `.sdkmanrc` file committed at the repo root — `java=21.0.13.1+2-tem` — so the repo itself
tells the machine what it needs. (One-time precondition: SDKMAN itself is installed.) Their
default Java 25 is untouched for other projects.

**Question 2.** `java -version` in your terminal says Temurin 21, but could Maven still be
building with a different Java? What line in which command's output proves it isn't?

**Answer.** Yes, in principle. The terminal's `java` is found through PATH, but Maven locates
its Java through `JAVA_HOME` — two different mechanisms that can point at two different JDKs
(e.g. JAVA_HOME left pointing at an old Homebrew JDK). The proof is the `Java version:` line
of `mvn -version`: it must show `vendor: Eclipse Adoptium` and a `runtime:` path inside
`~/.sdkman/candidates/java/...`. SDKMAN prevents the mismatch by setting JAVA_HOME to the same
`current` shortcut PATH uses.

**Question 3.** When SDKMAN "switches" your Java version, what actually changes on disk — and
why is it instant even though a JDK is hundreds of megabytes?

**Answer.** Only the `current` symlink under `~/.sdkman/candidates/java/` is repointed from
one version folder to another (plus JAVA_HOME/PATH already referencing that shortcut). Every
installed JDK already sits whole in its own folder, so nothing is copied or moved — repointing
a shortcut is a tiny metadata change, hence instant.

### Your doubts

None in this lesson.

---

## Lesson 2 — Step 2: The container runtime and the rest of the toolbox

### Definition

The **container runtime** is the thing that actually runs containers on the Mac. Containers
are a feature of the **Linux kernel**, and macOS has a different kernel — so every
"Docker for Mac" product (OrbStack, Docker Desktop, Colima) does the same job: it quietly runs
**one small Linux virtual machine** in the background, and the `docker` command you type on
the Mac is forwarded into that VM, where the real work happens.

The **toolbox** is nine small command-line tools installed today so that later phases are
about the work, not about installation problems.

### Why it is needed

- Without a runtime, `docker` is just a client with nobody to talk to —
  `Cannot connect to the Docker daemon` is what that looks like.
- The whole project runs on containers: six infrastructure services in this task alone.
- Tools like `kind`, `helm`, and async-profiler are not needed for weeks, but installing them
  now surfaces install friction today, while the stakes are low.

### How it works, step by step

1. The Mac's kernel cannot run Linux containers. So the runtime boots one lightweight Linux
   VM (seconds, not minutes) and keeps it running in the background.
2. Inside that VM runs the **Docker engine** (the daemon) — the server that builds images,
   starts containers, and manages volumes.
3. The `docker` command on the Mac is only a **client**. It sends requests through a socket
   file into the VM. `docker context ls` shows which socket, i.e. which runtime serves you.
4. Every container you ever start lives inside that **one** VM and shares its resources.
   The VM's memory is therefore a hard ceiling for all containers put together.
5. OrbStack grows and shrinks its memory use automatically. **Colima's VM has a fixed size
   you set yourself** — its default is 2 CPUs and 2 GiB, which is far too small for this
   project's stack. When containers outgrow the VM, the Linux kernel inside it starts killing
   the biggest processes: containers die "randomly" with exit code 137 (killed).

### Example — real commands, real output from this machine

**Which runtime serves this machine?**

```bash
docker context ls
```

```text
NAME       DESCRIPTION   DOCKER ENDPOINT
colima *   colima        unix:///Users/pavankumar/.colima/default/docker.sock
```

This machine runs **Colima** — the task suggests OrbStack, but all three are the same kind of
thing and the `docker` commands are identical, so Colima stays (switching would discard the
images and volumes from F7.8/F7.9 for no gain). On the fresh second laptop, OrbStack is the
better pick.

**The RAM trap, found for real.** The host has 36 GiB RAM and 14 CPUs, but:

```bash
colima list
```

```text
PROFILE    STATUS     ARCH       CPUS    MEMORY    DISK
default    Running    aarch64    2       2GiB      100GiB
```

2 GiB total for every container at once — Kafka alone wants about 1 GiB. The fix (settings
persist across restarts; the VM's disk with all images and volumes is untouched):

```bash
colima stop && colima start --cpu 4 --memory 8
colima list
```

```text
PROFILE    STATUS     ARCH       CPUS    MEMORY    DISK
default    Running    aarch64    4       8GiB      100GiB
```

**Side effect worth remembering:** stopping the VM stopped the two F7.8 practice Postgres
containers (`pg`, `pg17`), and they did **not** come back when the VM returned — plain
`docker run` containers have restart policy `no`. They had to be started by hand:

```bash
docker start pg pg17
```

This is exactly the problem `restart: unless-stopped` solves in Step 4.

**The toolbox.** Already present: `jq` (macOS ships it now) and `pgcli` (from F5 work).
Installed with Homebrew: `kind`, `kubectl`, `helm`, `k6`, `httpie`, `websocat`.
Installed by download (no brew formula): **async-profiler**, unpacked to
`~/tools/async-profiler-4.6-macos/`.

| Tool | Plain meaning | First really used |
|---|---|---|
| `kind` | Runs a whole practice Kubernetes cluster inside Docker containers | Phase 13 |
| `kubectl` | The command that talks to any Kubernetes cluster | Phase 13 |
| `helm` | Package manager for Kubernetes apps | Phase 13 |
| `k6` | Load tester — hammers an API on purpose to find its limits | Perf work |
| `jq` | Slices JSON on the command line | Constantly |
| `httpie` (`http`) | A friendlier `curl` for calling APIs | Constantly |
| `websocat` | Like `curl`, but for WebSockets | WebSocket phase |
| `pgcli` | Postgres shell with autocomplete | Constantly |
| async-profiler (`asprof`) | Shows where a running Java program spends its CPU | Phase 12 |

**The sweep — every tool answers a version command:**

```text
docker:    Docker version 29.9.2, build a82e528901
kind:      kind v0.33.0 go1.27.0 darwin/arm64
kubectl:   Client Version: v1.37.2
helm:      version.BuildInfo{Version:"v4.3.2", ...}
k6:        k6 v2.4.0 (commit/devel, go1.27.4, darwin/arm64)
jq:        jq-1.7.1-apple
httpie:    3.2.4
websocat:  websocat 1.14.2
pgcli:     Version: 4.7.1
asprof:    Async-profiler 4.6 built on Oct 12 2026
```

### Important terms

| Term | Meaning |
|---|---|
| Kernel | The core program of an OS: owns the hardware, schedules processes, hands out memory. |
| Container runtime | The product that runs containers on a Mac: one managed Linux VM + Docker engine. |
| Docker engine / daemon | The server inside the VM that actually builds images and runs containers. |
| Docker client | The `docker` command on the Mac; only sends requests to the engine. |
| `docker context ls` | Shows which runtime (socket) the client talks to. |
| Colima | Lightweight runtime; fixed-size VM set with `colima start --cpu N --memory M`. |
| OrbStack | Runtime with automatic memory management; best pick on a fresh machine. |
| Exit code 137 | Process killed (128+9). In Docker it almost always means out-of-memory kill. |

### Common mistakes

- **Leaving Colima at its 2 GiB default** and then blaming Kafka when containers die with
  exit 137. The VM's size is the ceiling for all containers combined.
- **Thinking each container is its own VM.** There is exactly one Linux VM; every container
  is a process inside it.
- **`Cannot connect to the Docker daemon`** does not mean Docker is broken — the runtime
  (OrbStack app / Colima VM) simply is not running.
- **Expecting `docker run` containers to survive a runtime restart.** Default restart policy
  is `no`; nothing comes back by itself until Compose sets `restart: unless-stopped` (Step 4).

### Where it is used

- Steps 3–9 of this task run six services on this runtime.
- Phase 12 uses async-profiler on the running Java services.
- Phase 13 uses kind + kubectl + helm for a Kubernetes cluster inside this same Docker.

### Link to the task

- Step 2's "you know it worked when", word for word: `docker ps` runs without an error, and
  each tool prints a version. Both shown above with real output.
- The task's "Where people get stuck: RAM" warning is the Colima 2 GiB finding — found and
  fixed here before it could bite.

### Do it yourself

On the second laptop (assume nothing installed), in order:

1. **Container runtime — OrbStack on a fresh machine:**

   ```bash
   brew install --cask orbstack
   open -a OrbStack
   ```

   Let its first-run setup finish (icon in the menu bar), then:

   ```bash
   docker ps
   ```

   Expect an empty table (just the header), no error.
   Failure mode: `Cannot connect to the Docker daemon` → the OrbStack app is not running.

2. **Command-line tools** (this laptop also needs `jq` and `pgcli`):

   ```bash
   brew install kind kubectl helm k6 jq httpie websocat pgcli
   ```

3. **async-profiler** — a download, not a formula:

   ```bash
   mkdir -p ~/tools && cd ~/tools
   TAG=$(curl -s https://api.github.com/repos/async-profiler/async-profiler/releases/latest | jq -r .tag_name)
   curl -sL -o ap.zip "https://github.com/async-profiler/async-profiler/releases/download/${TAG}/async-profiler-${TAG#v}-macos.zip"
   unzip -q ap.zip && rm ap.zip
   ~/tools/async-profiler-*/bin/asprof --version
   ```

   Expect: `Async-profiler 4.6 built on ...` (or newer).

4. **The sweep** — every line must print a version, none `command not found`:

   ```bash
   docker ps
   kind version
   kubectl version --client
   helm version
   k6 version
   jq --version
   http --version
   websocat --version
   pgcli --version
   ```

**Done when:** `docker ps` runs without error and all nine tools print a version.

### Summary for revision

- Containers are a Linux-kernel feature; a Mac runs them inside one hidden Linux VM.
- OrbStack / Docker Desktop / Colima = three brands of "managed Linux VM + Docker engine".
- The `docker` command is only a client; `docker context ls` shows who it talks to.
- The VM's memory is the ceiling for all containers combined. Colima default = 2 GiB = trap.
  Fix: `colima stop && colima start --cpu 4 --memory 8`. Exit 137 = OOM kill.
- Install the whole toolbox now (`kind`, `kubectl`, `helm`, `k6`, `jq`, `httpie`, `websocat`,
  `pgcli`, async-profiler) so later phases start at the work.
- Plain `docker run` containers do not survive a runtime restart — restart policy arrives in
  Step 4.

### Check questions, with answers

**Question 1.** Your Mac does not run Linux. Where exactly do your containers actually run —
and what are OrbStack, Docker Desktop, and Colima in one sentence?

**Answer.** Inside one small, hidden Linux virtual machine that runs in the background on the
Mac. OrbStack, Docker Desktop, and Colima are three brands of the same thing: a managed Linux
VM plus the Docker engine inside it, which the Mac's `docker` command talks to through a
socket. All containers share that one VM — no container is its own VM.

**Question 2.** Three weeks from now, `make up` starts failing: containers randomly die with
exit code 137. On this machine, what single number is your first suspect, what command shows
it, and what command fixes it?

**Answer.** The Colima VM's fixed memory size — the ceiling for all containers combined.
`colima list` shows it (this machine's default was 2 GiB). Fix:
`colima stop && colima start --cpu 4 --memory 8` — the setting persists, and the VM's disk
(images, volumes) is untouched. Exit 137 means killed (128 + signal 9): the Linux kernel
inside the VM ran out of memory and killed the biggest process, usually Kafka or Postgres.

**Question 3.** When the Colima VM was restarted, the two Postgres practice containers stayed
stopped until someone started them by hand. Why didn't they come back on their own, and which
upcoming Compose setting exists precisely for this?

**Answer.** They were started with plain `docker run`, whose default restart policy is `no` —
Docker never restarts such containers after a crash or a daemon/VM restart. Step 4 gives every
long-running service `restart: unless-stopped`, which brings it back after a crash, a Docker
restart, or a reboot — unless you stopped it yourself. (The one exception stays `no`: the `mc`
bootstrap container, which is supposed to run once and exit.)

### Your doubts

None in this lesson.

---

## Lesson 3 — Step 3 begins: container vs VM (the kernel), and Postgres starts the infra file

### Definition

A **kernel** is the core program of an operating system: it owns the hardware, decides which
process runs next, hands out memory, and performs all file and network operations.

A **virtual machine** is software pretending to be a whole computer, so a complete OS —
**with its own kernel** — must boot inside it.

A **container** boots nothing: it is ordinary processes running on the host's one kernel,
which shows them a private, limited view (own filesystem, own process numbering, own network
ports — Linux *namespaces*) and caps what they consume (Linux *cgroups*). No second kernel,
no fake hardware. Isolation by bookkeeping, not by pretending to be a computer.

### Why it is needed

- This explains every speed and size fact about Docker: containers start in milliseconds
  (starting a container = starting a process) and cost megabytes, while VMs boot for a minute
  and reserve gigabytes (they must run a whole OS).
- It explains the Mac setup: containers need a *Linux* kernel, so one hidden Linux VM (Colima
  here) hosts **all** containers. One VM total — a container is *not* its own VM.
- Step 3's work starts here: the one Compose file all six infra services will live in,
  grown one proven service at a time — because adding all six at once and then debugging is
  the slow path.

### How it works, step by step

1. A VM at startup: fake hardware appears, a full OS boots on it — its own kernel loads, an
   init system starts, dozens of system services appear. Minutes and gigabytes.
2. A container at startup: the kernel is told "start this process; show it the files from
   this image as its whole filesystem; give it its own process numbering and network; cap its
   memory." Milliseconds.
3. Inside the container, the process genuinely believes it is PID 1 on its own machine and
   that it owns its ports — the kernel's namespaces renumber and re-scope everything it sees.
4. Outside, it is just another process on the one shared kernel.
5. Consequence for ports: every container may *listen* on 5432 privately, but publishing to
   the host's 5432 (`ports: "5432:5432"`) is exclusive — the host has only one port 5432.

### Example — real commands, real output from this machine

**The file, deliberately bare** (volumes, healthchecks, restart policy and the named network
each arrive in Steps 4–5 as their own lesson) — `deploy/compose/docker-compose.infra.yml`:

```yaml
name: infra

services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: dev # literal for now - Step 8 moves it to .env
      POSTGRES_DB: app
    ports:
      - "5432:5432"
```

- `name: infra` — Compose names containers `<project>-<service>-<n>`: `infra-postgres-1`.
- `environment` — read once at first initialization: superuser, password, a database `app`.
- `ports` — host 5432 forwards into the container's 5432.

**First start — a real failure:**

```bash
docker compose -f deploy/compose/docker-compose.infra.yml up -d
```

```text
 Container infra-postgres-1 Error response from daemon: ...
 Bind for 0.0.0.0:5432 failed: port is already allocated
```

| Piece of the error | Plain meaning |
|---|---|
| `Bind for 0.0.0.0:5432` | The container asked to own port 5432 on the host |
| `port is already allocated` | Another container already publishes 5432 |
| Who? | `docker ps`, PORTS column: the F7.8 practice container `pg` |

Fix: the infra stack owns 5432 from now on, so the practice containers were **stopped, not
deleted** (`docker stop pg pg17` — their data is intact; `docker start pg` revives them).

**Second start — success, and proof it genuinely works:**

```text
$ docker compose -f deploy/compose/docker-compose.infra.yml up -d
 Container infra-postgres-1 Started

$ docker compose -f deploy/compose/docker-compose.infra.yml ps
NAME               IMAGE                STATUS   PORTS
infra-postgres-1   postgres:17-alpine   Up       0.0.0.0:5432->5432/tcp

$ docker exec infra-postgres-1 psql -U postgres -d app -c "select version();"
 PostgreSQL 17.11 on aarch64-unknown-linux-musl, compiled by gcc (Alpine 15.2.0) 15.2.0, 64-bit
```

**The kernel lesson made visible — everything running inside the container:**

```text
$ docker exec infra-postgres-1 ps
PID   USER     TIME  COMMAND
    1 postgres  0:00 postgres
   54 postgres  0:00 postgres: checkpointer
   55 postgres  0:00 postgres: background writer
   57 postgres  0:00 postgres: walwriter
   58 postgres  0:00 postgres: autovacuum launcher
   59 postgres  0:00 postgres: logical replication launcher
   67 root      0:00 ps
```

Six Postgres processes plus the `ps` itself. No booted OS, no login service, no kernel.
Postgres thinks it is PID 1 because the kernel shows it a renumbered private view. A real VM
could never look this empty.

### Important terms

| Term | Meaning |
|---|---|
| Kernel | Core OS program owning hardware, processes, memory, files, network. |
| Virtual machine | Software pretending to be a whole computer; boots a full OS + own kernel. |
| Container | Ordinary processes on the host's one kernel, with a namespaced view and cgroup caps. |
| Namespace | Kernel bookkeeping giving a container its private view: filesystem, PIDs, network. |
| cgroup | Kernel bookkeeping capping a container's memory/CPU. |
| PID 1 | First process. Each container's main process sees itself as PID 1 in its namespace. |
| `name:` (Compose) | Project name; containers become `<project>-<service>-<n>`. |
| `port is already allocated` | Another container already publishes that host port. |

### Common mistakes

- **Calling a container "a lightweight VM."** It is not a VM at all — no second kernel,
  no fake hardware. (The one Linux VM on a Mac is the *runtime's*, shared by all containers.)
- **Adding all six services at once, then debugging.** The slow path. One service, prove it,
  next.
- **Reading `port is already allocated` as "Postgres is broken."** It means the *host* port is
  taken — `docker ps` PORTS column finds the holder.
- **Expecting `environment:` changes to update an existing database.** Those variables are
  read once, at first initialization only.

### Where it is used

- Every service added in Steps 3–9 is "just processes on the shared kernel" — this is why six
  services on a laptop is feasible at all.
- The port-exclusivity rule returns every time something new publishes a host port (5432,
  6379, 9092, 8081, 9000, 8025...).
- Interviews love "container vs VM" — the kernel answer is the right one.

### Link to the task

- Task's "Learn this first", word for word: *what a container is and is not (it is not a
  virtual machine — the difference in terms of the kernel)* — this lesson.
- Step 3.1 and 3.2 done: the file exists at `deploy/compose/docker-compose.infra.yml`,
  Postgres 17 runs from it, and a connection was proven before touching the file again.

### Do it yourself

1. In the project repo root:

   ```bash
   mkdir -p deploy/compose
   ```

   Create `deploy/compose/docker-compose.infra.yml` with exactly the content shown above.

2. Start it:

   ```bash
   docker compose -f deploy/compose/docker-compose.infra.yml up -d
   ```

   Expect `Network infra_default Created`, then `Container infra-postgres-1 Started`.
   Failure mode: `Bind for 0.0.0.0:5432 failed: port is already allocated` → find the holder
   with `docker ps` (PORTS column, look for `5432->`), `docker stop <name>`, retry.

3. Prove it accepts connections:

   ```bash
   docker compose -f deploy/compose/docker-compose.infra.yml ps
   docker exec infra-postgres-1 psql -U postgres -d app -c "select version();"
   ```

   Expect STATUS `Up` and a `PostgreSQL 17.11 ...` row.
   (Interactive from the host: `pgcli -h localhost -p 5432 -u postgres -d app`, password `dev`.)

4. Look inside:

   ```bash
   docker exec infra-postgres-1 ps
   ```

   Expect ~7 processes with `postgres` as PID 1.

**Done when:** the `select version();` query answers, and you can say in one sentence why the
process list inside the container is so short.

### Summary for revision

- VM = fake computer, boots a full OS with its own kernel: minutes, gigabytes.
- Container = processes on the host's one kernel; namespaces give the private view, cgroups
  cap usage: milliseconds, megabytes.
- All containers on the Mac share one Linux kernel inside one runtime VM.
- Listening privately is per-container; publishing a host port is exclusive.
- Infra file grows one proven service at a time; Postgres 17 is in and answering queries.
- `environment:` init values apply only on first initialization.

### Check questions, with answers

**Question 1.** A VM takes a minute to start; the Postgres container took under a second.
What does a VM have to do at startup that a container never does?

**Answer.** Boot an entire operating system on pretend hardware: load its own kernel, start an
init system, bring up dozens of system services — before the actual program can run. A
container skips all of it: the host kernel is already running, so the container's main process
just starts, with the kernel showing it a private view of files, PIDs, and ports.

**Question 2.** The Mac ran the infra Postgres and, earlier, two more Postgres containers at
once. How many Linux kernels were involved in total, and where do they live?

**Answer.** One. It lives inside the single Colima VM, and all containers — however many —
share it. (The Mac's own macOS kernel also runs, hosting that VM, but no container gets a
kernel of its own.)

**Question 3.** Inside the container, Postgres believes it is PID 1 and that it owns port
5432. Both beliefs are false on the host. What kernel mechanism makes each private view
possible?

**Answer.** Namespaces — the kernel's per-container bookkeeping. A *PID namespace* renumbers
the container's processes starting from 1, so its main process sees itself as PID 1. A
*network namespace* gives the container its own network world — interfaces and ports — so it
can bind "its" 5432 regardless of the host or other containers. (The capping side — memory
and CPU limits — is cgroups, not namespaces.)

### Your doubts

None in this lesson.

---

## Lesson 4 — Step 3 continues: Redis joins the file

### Definition

**Redis** is a key-value store that keeps everything in RAM — honest mental model: a giant
`HashMap<String, value>` served over the network, answering in microseconds. In this project
**Postgres is truth, Redis is speed**: Redis carries what is read constantly but can be lost
and rebuilt — cached query results, user sessions, rate-limit counters.

### Why it is needed

- Losing a cache costs a moment of slowness while it refills; losing the database costs the
  business. Two different jobs, two different tools.
- Every later phase that touches caching, sessions, or rate limiting assumes Redis is one
  `make up` away.

### How it works, step by step

1. Add four lines to `deploy/compose/docker-compose.infra.yml`:

   ```yaml
     redis:
       image: redis:7-alpine
       ports:
         - "6379:6379"
   ```

2. **No `environment:` block, on purpose.** Postgres needed one because its first boot must
   *create things* (superuser, password, a database), fed in as environment variables. Redis
   creates nothing: it is born useful, an empty map ready for commands. By default it also has
   no password — acceptable while it is reachable only from this laptop.
3. **Port 6379** is Redis's canonical port, as 5432 is Postgres's.
4. `docker compose up -d` converges the stack: Postgres (already matching the file) is left
   alone; Redis (missing) is created.

### Example — real commands, real output from this machine

```bash
docker compose -f deploy/compose/docker-compose.infra.yml up -d
```

```text
 Container infra-postgres-1 Running      <- untouched
 Container infra-redis-1 Created
 Container infra-redis-1 Started
```

`up -d` does not mean "restart everything". It means "**make reality match the file**" —
create what is missing, recreate what changed, leave what matches alone. That is why it is
safe to run at any moment.

```bash
docker exec infra-redis-1 redis-cli ping
docker exec infra-redis-1 redis-cli set greeting "hello from redis"
docker exec infra-redis-1 redis-cli get greeting
```

```text
PONG
OK
"hello from redis"
```

`PONG` proves the full round trip: client in, server alive, command parsed, answer out.
And a flag planted for Step 4: `greeting` lives in RAM plus, at best, a snapshot file in the
container's writable layer — **nothing in the stack survives `docker compose down` yet.**

### Important terms

| Term | Meaning |
|---|---|
| Redis | In-memory key-value store; microsecond answers; cache/session/rate-limit duty. |
| `redis-cli` | Redis's command-line client, shipped inside the image. |
| `PING`/`PONG` | Redis's liveness command and its fixed answer; later the healthcheck test. |
| 6379 | Redis's canonical port. |
| Converge | What `up -d` does: change only what differs from the file. |

### Common mistakes

- **Treating Redis as a database.** It is the speed layer; Postgres is the truth layer.
- **Thinking `up -d` restarts services.** It converges; untouched services keep running.
- **Expecting Redis data to survive `down`.** RAM + writable layer = gone with the container
  (until Step 4's volume).

### Where it is used

- Caching, sessions, and rate limiting in later phases.
- `redis-cli ping` becomes the Redis healthcheck test in Step 5.
- The convergence idea returns every time a service is added to this file.

### Link to the task

- Step 3.3, word for word: "Add Redis 7. Start the stack again; `redis-cli ping` inside the
  container should answer `PONG`." Done, with real output above.

### Do it yourself

1. Append the `redis:` block (above) to `deploy/compose/docker-compose.infra.yml`, same
   indentation as `postgres:` — a second entry under `services:`.
2. Converge and prove:

   ```bash
   docker compose -f deploy/compose/docker-compose.infra.yml up -d
   docker exec infra-redis-1 redis-cli ping          # expect: PONG
   docker exec infra-redis-1 redis-cli set greeting "hello"   # expect: OK
   docker exec infra-redis-1 redis-cli get greeting  # expect: "hello"
   ```

   Also expect `infra-postgres-1 Running` (untouched) in the `up -d` output.
   Failure mode: `Bind for 0.0.0.0:6379 failed: port is already allocated` → find the holder:
   `docker ps` (PORTS column) for containers, `lsof -i :6379` for non-container processes
   (e.g. a `brew services` Redis), stop it, retry.

**Done when:** `ping` answers `PONG` and you can say what `up -d` did to Postgres and why.

### Summary for revision

- Redis = RAM-speed key-value store. Postgres = truth, Redis = speed.
- Four lines, no env block: Redis creates nothing at first boot; no password by default.
- `up -d` converges reality to the file; safe anytime.
- `PONG` = full round trip proven. Data does not survive `down` yet — Step 4's job.

### Check questions, with answers

**Question 1.** Re-running `up -d` printed `infra-postgres-1 Running` and did not restart it.
What does `docker compose up -d` actually promise — and why does that make it safe to run at
any moment?

**Answer.** It promises to make reality match the file: create services that are missing,
recreate services whose definition changed, and leave running services that already match
untouched. Because it only changes what differs, running it again when nothing differs does
nothing — so you can run it whenever, without fear of restarting a working database.

**Question 2.** Postgres needed an `environment:` block; Redis didn't. What does Postgres have
to do on first boot that Redis doesn't?

**Answer.** Postgres must create persistent things before it is usable: the superuser account,
its password, and an initial database — and those names arrive as environment variables read
at first initialization. Redis has no accounts or databases to create; it starts as an empty
in-memory map, immediately useful, and by default without a password locally.

**Question 3.** The key `greeting` is in Redis now. After `docker compose down` then `up -d`,
will `get greeting` still answer? Where exactly was that data living?

**Answer.** No — it is gone. The value lived in the container's RAM, and at best in a snapshot
file inside the container's writable layer. `down` deletes the container and its writable
layer, so both copies vanish; the fresh container starts as an empty map. A named volume for
Redis's `/data` folder (Step 4) is what lets snapshots outlive the container.

### Your doubts

None in this lesson.

---

## Lesson 5 — Step 3 continues: Kafka in KRaft mode

### Definition

**Kafka** is a message log service: programs called *producers* append messages to named feeds
called *topics*; programs called *consumers* read them in order, each at its own pace. A
shared, append-only notebook — writers add lines at the end, every reader keeps their own
bookmark.

**Cluster metadata** is the team bookkeeping of a Kafka cluster: which topics exist, which
broker stores which part, who is in charge of what.

**KRaft** (Kafka Raft) is Kafka storing that metadata *in itself* — using Raft, a recipe by
which several computers agree on a shared log even if some crash — instead of in a separate
ZooKeeper service. One container instead of two. Kafka 4.x removed ZooKeeper support entirely.

### Why it is needed

- Direct API calls make the sender wait, lose messages when the receiver is down, and require
  changes for every new reader. A log in the middle fixes all three: the sender drops the
  message and moves on; the receiver catches up from its bookmark; a new reader starts
  tomorrow without the writer changing anything. (The real work is Phase 6; today it must run.)
- The old two-service setup (Kafka + ZooKeeper) meant two configs, two things to keep healthy,
  and "Kafka is fine but ZooKeeper isn't" failures. KRaft deletes that whole class of problems.

### How it works, step by step

The service block decodes into four groups.

1. **Identity — one node wearing both hats:**

   ```yaml
   KAFKA_NODE_ID: 1                              # this server's number in the team
   KAFKA_PROCESS_ROLES: broker,controller        # both jobs in one process
   KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093  # the metadata committee: member 1, here
   CLUSTER_ID: 5L6g3nShT-eMCtK--X86sw            # the cluster's identity stamp
   ```

   KRaft has two jobs: the **broker** stores and serves messages; the **controller** keeps the
   metadata and makes cluster decisions. Production separates them; a laptop merges them. The
   committee has one member: us. The fixed `CLUSTER_ID` matters from Step 4 on: Kafka's data
   carries this stamp, and a restart under a different ID refuses to start.

2. **Doors (listeners) — the part that confuses everyone.** A *listener* is a door Kafka
   accepts connections on. The trap: Kafka's protocol replies to any connection with the
   address to use for real traffic — the **advertised** address. If that handed-back address
   is unreachable from where the client stands, you get the classic "connects fine, then
   immediately dies".

   ```yaml
   KAFKA_LISTENERS: PLAINTEXT://:29092,PLAINTEXT_HOST://:9092,CONTROLLER://:9093
   KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
   ```

   | Who connects | Reaches Kafka at | Kafka hands back | Why it works |
   |---|---|---|---|
   | Other containers | `kafka:29092` | `kafka:29092` | service-name DNS inside the Compose network |
   | Programs on the Mac | `localhost:9092` | `localhost:9092` | 9092 is published to the host |

   The third door (`CONTROLLER://:9093`) is the controller role's internal channel. Supporting
   lines: `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP` (all doors speak unencrypted PLAINTEXT — fine
   on a laptop), `KAFKA_CONTROLLER_LISTENER_NAMES` and `KAFKA_INTER_BROKER_LISTENER_NAME`
   (which door each role uses).

3. **Single-node sizing.** Kafka keeps its own bookkeeping in internal topics and by default
   insists on three copies on three brokers. We have one:

   ```yaml
   KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
   KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
   KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
   ```

4. **Heap.** Kafka is a Java program defaulting to 1 GiB of heap;
   `KAFKA_HEAP_OPTS: -Xmx512m -Xms512m` halves that for the laptop's RAM budget.

### Example — real commands, real output from this machine

Image pinned to the newest release found on Docker Hub: `apache/kafka:4.4.0`, port `9092:9092`.
Full block in `deploy/compose/docker-compose.infra.yml`.

```text
$ docker compose -f deploy/compose/docker-compose.infra.yml up -d
 Container infra-postgres-1 Running
 Container infra-redis-1 Running
 Container infra-kafka-1 Started
```

**A real failure first** — the proof command as most tutorials write it:

```bash
docker exec infra-kafka-1 kafka-topics.sh --bootstrap-server localhost:9092 --list
```

```text
OCI runtime exec failed: ... exec: "kafka-topics.sh": executable file not found in $PATH
```

| Piece of the error | Plain meaning |
|---|---|
| `exec: "kafka-topics.sh"` | Docker tried to start this program inside the container |
| `not found in $PATH` | The `apache/kafka` image does not put its scripts on the PATH |
| Fix | Full path: `/opt/kafka/bin/kafka-topics.sh` — the Step 5 healthcheck needs this too |

**With the path fixed — list, create, list, delete:**

```bash
docker exec infra-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker exec infra-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic smoke-test --partitions 1 --replication-factor 1
docker exec infra-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker exec infra-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --delete --topic smoke-test
```

```text
                          <- empty list, no error: the broker answered
Created topic smoke-test.
smoke-test
```

(Inside the container `localhost:9092` works because the `PLAINTEXT_HOST` door binds there
too.) And the task's RAM worry, measured:

```text
NAME               MEM USAGE / LIMIT     CPU %
infra-kafka-1      311.2MiB / 7.737GiB   4.29%
infra-redis-1      13.48MiB / 7.737GiB   0.94%
infra-postgres-1   36.79MiB / 7.737GiB   0.00%
```

### Important terms

| Term | Meaning |
|---|---|
| Producer / consumer | Program that appends to / reads from a topic. |
| Topic | A named, append-only feed of messages. |
| Broker | One Kafka server; stores and serves messages. |
| Controller | The role that keeps cluster metadata and makes cluster decisions. |
| Cluster metadata | Which topics exist, who stores what, who leads. |
| ZooKeeper | The separate metadata service of old setups; gone in Kafka 4.x. |
| KRaft | Kafka storing its own metadata via the Raft agreement recipe. |
| Listener | A door Kafka accepts connections on. |
| Advertised listener | The return address Kafka hands back for real traffic — must be reachable *from the client's world*. |
| Replication factor | How many brokers hold a copy; internal topics default to 3, laptop has 1. |
| `/opt/kafka/bin/` | Where the `apache/kafka` image hides its command-line scripts. |

### Common mistakes

- **One advertised address for everybody.** A container handed `localhost:9092` dials itself;
  a Mac program handed `kafka:29092` can't resolve the name. Two worlds need two doors.
- **Reading "Up" in `docker ps` as "ready".** A started Kafka still needs seconds to become a
  ready Kafka; too-early clients see `Broker may not be available`. (Step 5's whole subject.)
- **Forgetting the single-node replication settings** — internal topics can never be created,
  and everything that needs them fails mysteriously.
- **Using `kafka-topics.sh` bare in this image** — not on PATH; use `/opt/kafka/bin/...`.

### Where it is used

- Schema Registry (next lesson) connects to `kafka:29092` — the container door.
- The Spring services of later phases connect to `localhost:9092` — the host door.
- Phase 6 (events, sagas, CQRS) lives on this broker; Step 5/6 of this task use its
  healthcheck as the readiness example.

### Link to the task

- Step 3.4, word for word: "Add Kafka in KRaft mode — Kafka manages its own cluster metadata,
  so you run one Kafka container and no separate ZooKeeper container." Done: one container,
  topics created and deleted, no ZooKeeper anywhere.
- The New-words table's **KRaft** entry is this lesson; its **healthcheck** entry meets the
  `/opt/kafka/bin/` finding in Step 5.

### Do it yourself

1. Add the `kafka:` block to `deploy/compose/docker-compose.infra.yml` exactly as in this
   repo's file (identity, doors, sizing, heap).
2. Start and wait:

   ```bash
   docker compose -f deploy/compose/docker-compose.infra.yml up -d
   ```

   First time pulls ~400 MB. **Wait ~10 seconds** after `Started` — a started Kafka is not yet
   a ready Kafka.
3. Prove it, full path required:

   ```bash
   docker exec infra-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
   docker exec infra-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
     --create --topic smoke-test --partitions 1 --replication-factor 1
   docker exec infra-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
   docker exec infra-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
     --delete --topic smoke-test
   ```

   Expect: empty, `Created topic smoke-test.`, `smoke-test`, silence.
   Failure modes: `executable file not found in $PATH` → use the full `/opt/kafka/bin/` path.
   `Broker may not be available` → too early; wait a few seconds and retry.

**Done when:** the create/list/delete cycle works and there is no ZooKeeper container anywhere
— and you can say why.

### Summary for revision

- Kafka = append-only message feeds; sender doesn't wait, reader catches up, many readers.
- Cluster metadata = team bookkeeping. Old home: ZooKeeper (second service). New home: inside
  Kafka itself — KRaft, via Raft agreement. Kafka 4.x is KRaft-only.
- One node, two hats: `broker,controller`. Committee of one: `1@kafka:9093`. Fixed
  `CLUSTER_ID` so Step 4's volume survives restarts.
- Listener = door; advertised listener = the return address handed back. Containers get
  `kafka:29092`, the Mac gets `localhost:9092`. Wrong world → "connects then dies".
- Internal topics default to 3 copies; single node needs the three `...FACTOR: 1` lines.
- Scripts live at `/opt/kafka/bin/`. Kafka idles at ~310 MiB with a 512 MiB heap.

### Check questions, with answers

**Question 1.** Old Kafka tutorials always show two containers. What did the second one do,
and what exactly absorbed that job in our single-container setup?

**Answer.** The second container was ZooKeeper, a separate service that stored the cluster
metadata — which topics exist, which broker holds what, who leads. KRaft absorbed it: Kafka's
own **controller** role now stores that metadata inside Kafka itself, kept consistent by the
Raft agreement recipe. Our single node runs `KAFKA_PROCESS_ROLES: broker,controller`, so one
process plays both parts — and Kafka 4.x couldn't use ZooKeeper even if asked.

**Question 2.** Why does our Kafka need two client doors with different advertised addresses —
what breaks if a container is handed `localhost:9092`, and what breaks if your Mac is handed
`kafka:29092`?

**Answer.** Because Kafka replies to every client with "use this address for real traffic",
and that address must be reachable *from the client's world*. A container handed
`localhost:9092` would dial its **own** localhost — there's no Kafka there, connection
refused. The Mac handed `kafka:29092` would fail DNS — the name `kafka` only resolves inside
the Compose network. So: door `PLAINTEXT` advertises `kafka:29092` for containers, door
`PLAINTEXT_HOST` advertises `localhost:9092` for the host. Each world gets a return address
that works where it stands.

**Question 3.** Right after `up -d`, `docker ps` says the Kafka container is `Up`, yet
`kafka-topics.sh --list` answers `Broker may not be available`. Both are telling the truth.
How?

**Answer.** `Up` means only that the container's main **process exists** — Docker started it.
Inside, Kafka is still initializing: formatting/reading its KRaft metadata, electing its
controller, binding listeners. Until that finishes, the process is alive but not yet
*accepting work* — so a client truthfully gets "broker not available". Started and ready are
different states; healthchecks (Steps 4–5) exist to encode "ready", and `depends_on` without
one is exactly how flaky startups happen.

### Your doubts

None in this lesson.

---

## Lesson 6 — Step 3 continues: Schema Registry

### Definition

The **Schema Registry** is a small service that stores the agreed shape — the **schema**, a
field list with types — of every Kafka message type, with full version history. Producers and
consumers both check against it, and the registry **refuses** schema changes that would break
existing readers. That refusal policy is the **compatibility rule**; the default `BACKWARD`
means "a new version must still let readers of old data work."

### Why it is needed

With Kafka, the writer and reader of a message never meet. A producer writes
`{"orderId": 42, "amount": 99.5}` today; a consumer may parse that message next week, in a
different team's codebase. Nothing in Kafka checks that they agree on the shape — rename
`amount` to `totalAmount` in the producer and every consumer breaks at runtime, or silently
reads nulls. With a direct API call both ends at least fail together, loudly; Kafka's great
strength — writer and readers decoupled in time — is exactly what makes shape drift dangerous.
The registry is the enforced shared contract. (Per the task: today it only needs to start;
Phase 6 teaches real use.)

### How it works, step by step

1. The registry is a Java web service with a REST API on port 8081. Programs register schemas
   and fetch them by subject (message type) and version.
2. It is a **Kafka client itself**: it connects to the broker at `kafka:29092` — the
   container-world door from the Kafka lesson.
3. **It stores every schema inside Kafka**, in a topic named `_schemas`. The service is
   **stateless**: kill the container, start a fresh one, and it rebuilds its whole memory by
   replaying that topic. Consequences: it gets no volume in Step 4 (correctly — Kafka's volume
   is what protects the schemas), and this is the project's first sight of a big pattern:
   *state in the log, services disposable*.
4. Plain `depends_on: - kafka` promises **start order only** — "create Kafka's container
   first" — not readiness. This is the loaded gun of Steps 5–6: it gets upgraded to
   `condition: service_healthy`, then removed on purpose to watch the crash.

### Example — real commands, real output from this machine

The block in `deploy/compose/docker-compose.infra.yml`:

```yaml
  schema-registry:
    image: confluentinc/cp-schema-registry:8.4.1
    ports:
      - "8081:8081"
    depends_on: # start order only - Step 5 upgrades this to "wait until healthy"
      - kafka
    environment:
      SCHEMA_REGISTRY_HOST_NAME: schema-registry
      SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: kafka:29092 # the container-world door
      SCHEMA_REGISTRY_LISTENERS: http://0.0.0.0:8081
      SCHEMA_REGISTRY_HEAP_OPTS: -Xmx256m -Xms256m
```

(The image is Confluent's — the registry is a Confluent product that works fine against plain
Apache Kafka. Another Java program, so the heap is capped.)

```text
$ docker compose -f deploy/compose/docker-compose.infra.yml up -d
 Container infra-schema-registry-1 Started

$ curl -s http://localhost:8081/subjects
[]
$ curl -s http://localhost:8081/config
{"compatibilityLevel":"BACKWARD"}
```

`[]` is success: API alive, zero schemas registered. The twist — ask Kafka what topics exist:

```text
$ docker exec infra-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
__consumer_offsets
_schemas
```

`_schemas` is the registry's entire truth, living inside Kafka. (`__consumer_offsets` is
Kafka's own "reader bookmarks" topic, created when the registry connected as a consumer.)

### Important terms

| Term | Meaning |
|---|---|
| Schema | The agreed shape of a message: field names and types. |
| Subject | The registry's name for "all schema versions of one message type". |
| Compatibility rule | Which schema changes the registry refuses. `BACKWARD`: new version must not break readers of old data. |
| `_schemas` | The Kafka topic where the registry stores everything it knows. |
| Stateless service | Holds no data of its own; can be killed and replaced freely. |
| `depends_on` (plain) | Start order only — not readiness. |

### Common mistakes

- **Pointing the registry at `localhost:9092`.** Inside its container, localhost is itself —
  crash-loop. Container clients use `kafka:29092`.
- **Giving the registry a volume "to protect the schemas".** They live in Kafka's `_schemas`
  topic; Kafka's volume is their protection.
- **Reading plain `depends_on` as "waits until Kafka is ready".** It only orders container
  creation; on a cold start the registry can race ahead and crash (Steps 5–6 handle this).

### Where it is used

- Phase 6: producers and consumers register and fetch schemas as part of event-driven work.
- Steps 5–6 of this task: the registry-vs-unready-Kafka race is the demo case for
  `condition: service_healthy`.

### Link to the task

- Step 3.5, word for word: "Add Schema Registry... Today it only needs to start." Done: API
  answering on 8081, schemas stored in `_schemas`.
- New-words table's **Schema Registry** entry is this lesson.

### Do it yourself

1. Add the `schema-registry:` block above to the compose file.
2. Start (first pull is ~1 GB, allow a couple of minutes):

   ```bash
   docker compose -f deploy/compose/docker-compose.infra.yml up -d
   ```

   Wait ~15 seconds after `Started` — a Java service connecting to Kafka needs a moment.
3. Prove it:

   ```bash
   curl -s http://localhost:8081/subjects    # expect: []
   curl -s http://localhost:8081/config      # expect: {"compatibilityLevel":"BACKWARD"}
   docker exec infra-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
   # expect the list to contain: _schemas
   ```

   Failure modes: `curl: (7) Failed to connect` → too early (wait, retry) or the container
   died — `docker compose -f deploy/compose/docker-compose.infra.yml logs schema-registry`;
   if logs show it cannot reach Kafka, confirm the bootstrap address is `kafka:29092`. On a
   cold start it may crash once while Kafka initializes — the exact flakiness Steps 5–6 fix
   and then demonstrate.

**Done when:** `/subjects` answers `[]` and you can say where the registry keeps the schemas.

### Summary for revision

- Kafka decouples writer and reader in time → shape drift breaks things late and quietly →
  the registry is the enforced contract, with version history and compatibility rules.
- It is a Kafka client: `kafka:29092`, the container door.
- All schemas live in the `_schemas` topic inside Kafka; the registry itself is stateless —
  no volume in Step 4, and "state in the log, services disposable" is a pattern to remember.
- Plain `depends_on` = start order, not readiness.

### Check questions, with answers

**Question 1.** Kafka decouples writer and reader in time. Why does that very strength make a
schema registry more necessary than it is for direct API calls?

**Answer.** With a direct call, writer and reader interact live: a shape mismatch fails
immediately, loudly, and both teams notice together. With Kafka, a consumer may parse a
message written weeks ago by a team that has since changed its code — so a drift between what
was written and what readers expect surfaces late, far from its cause, sometimes as silent
nulls rather than errors. The registry restores a single, enforced contract both sides check
against, and its compatibility rules refuse the changes that would strand old data's readers.

**Question 2.** The registry's config says `kafka:29092`. Using the advertised-listener logic,
what exactly would go wrong with `localhost:9092` instead?

**Answer.** Inside the registry's container, `localhost` is the container's own private
loopback (its own network namespace) — nothing listens on 9092 there, so the connection is
refused and the registry crash-loops. And even reaching Kafka's host door wouldn't help: that
door *advertises* `localhost:9092`, so the registry would be handed an address that, from its
world, points back at itself. The container door `kafka:29092` is both reachable (service-name
DNS on the Compose network) and advertises an address correct for containers.

**Question 3.** You `docker rm -f` the Schema Registry container and start a new one — are the
registered schemas gone? Where do they live, and what does that mean for the volumes we add
in Step 4?

**Answer.** Not gone. Every schema lives in the `_schemas` topic inside Kafka; the fresh
registry container replays that topic at startup and rebuilds its entire memory. The registry
is stateless, so Step 4 gives it **no volume** — and that is correct. The data that actually
needs protecting is Kafka's, which is why Kafka gets one.

### Your doubts

None in this lesson.

---

## Lesson 7 — Step 3 continues: MinIO, image sourcing in 2026, and the run-once bootstrap

### Definition

**Object storage** stores whole files ("objects") under names ("keys") inside named containers
("buckets"), over plain HTTP: `PUT` a blob, `GET` it back, `DELETE` it. No partial edits, no
real folders. Amazon **S3** is the world's default object store and its HTTP API is the
de-facto standard. **MinIO** is a single program that speaks that exact API on the laptop —
code written against the real AWS S3 client library works unchanged, pointed at
`localhost:9000`. **`mc`** is MinIO's command-line client; a **run-once bootstrap container**
uses it to create the first bucket, then exits — exit code 0 meaning success.

### Why it is needed

- Rows (Postgres) and keys in RAM (Redis) don't fit profile photos, PDFs, video uploads.
  Databases are bad at blobs; plain filesystems don't scale across servers. Object storage is
  the third shape of data.
- Dev-prod parity: the app uses the real S3 SDK locally; shipping to production changes an
  endpoint and credentials, zero code.
- A fresh MinIO has no buckets, and apps should not create infrastructure at runtime — hence
  the bootstrap job.

### How it works, step by step

1. **Sourcing reality (hit live, November 2026).** Every tutorial says `image: minio/minio`.
   What actually happened:

   | Attempted source | Real result | Meaning |
   |---|---|---|
   | `minio/minio` on Docker Hub | `404 object not found` | repo deleted |
   | `quay.io/minio/minio` | `401 UNAUTHORIZED` (even with anonymous token) | made private |
   | `dl.min.io` binaries | `HTTP 410 Gone` | download server killed |

   MinIO the company went commercial in 2025 and withdrew its public artifacts. Lessons:
   an image reference can name any registry (`quay.io/...`, `cgr.dev/...` — Docker Hub is only
   the default); registries answer `401` instead of `404` to avoid confirming private repo
   names; "public today" is a fact, not a promise.
2. **Choices made.** Server: `cgr.dev/chainguard/minio:latest` — Chainguard rebuilds current
   MinIO from source (got `RELEASE.2026-11-16`, three days old). Cost: free tier is
   `:latest`-only, so this is the one deliberately unpinned image in the file, with a comment
   saying why. Client: `bitnamilegacy/minio-client:2025.7.21` — frozen-but-public archive;
   a 2025 `mc` speaks S3 fine forever.
3. **The bootstrap container** overrides its entrypoint to `/bin/sh -c` and runs a tiny
   script: an `until` retry loop around `mc alias set` (because `depends_on` orders creation,
   not readiness), then `mc mb --ignore-existing` (idempotent — safe on every `up`), then
   exits. It is a *job*, not a *service*.

### Example — real commands, real output from this machine

The blocks in `deploy/compose/docker-compose.infra.yml`:

```yaml
  # MinIO pulled its public images in 2025 (Docker Hub deleted, quay.io private,
  # dl.min.io returns 410). Chainguard rebuilds current MinIO from source; its free
  # tier offers :latest only, so this one image cannot be version-pinned.
  minio:
    image: cgr.dev/chainguard/minio:latest
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000" # S3 API
      - "9001:9001" # web console
    environment:
      MINIO_ROOT_USER: minio
      MINIO_ROOT_PASSWORD: minio12345 # literal for now - Step 8 moves it to .env

  minio-init: # runs once, creates the first bucket, exits. Exit 0 = success.
    # mc from the frozen-but-public Bitnami archive; a 2025 mc speaks S3 fine forever.
    image: bitnamilegacy/minio-client:2025.7.21
    depends_on:
      - minio
    entrypoint: ["/bin/sh", "-c"]
    command:
      - |
        until mc alias set local http://minio:9000 minio minio12345; do
          echo "waiting for minio..."; sleep 1
        done
        mc mb --ignore-existing local/media
        echo "bucket 'media' ready"
```

**The first run, with the retry loop earning its keep:**

```text
$ docker compose -f deploy/compose/docker-compose.infra.yml logs minio-init
minio-init-1 | mc: <ERROR> ... dial tcp 172.18.0.6:9000: connect: connection refused.
minio-init-1 | waiting for minio...
minio-init-1 | Added `local` successfully.
minio-init-1 | Bucket created successfully `local/media`.
minio-init-1 | bucket 'media' ready
```

The first attempt hit a MinIO that existed but was not ready — the same started-vs-ready
villain as Kafka's "broker may not be available". The loop absorbed it.

```text
$ docker compose -f deploy/compose/docker-compose.infra.yml ps -a
infra-minio-1        Up 10 seconds
infra-minio-init-1   Exited (0) 9 seconds ago     <- success: job done
```

**Proofs:**

```bash
docker run --rm --network infra_default --entrypoint /bin/sh bitnamilegacy/minio-client:2025.7.21 \
  -c "mc alias set local http://minio:9000 minio minio12345 >/dev/null && mc ls local"
curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:9000/minio/health/live
```

```text
[2026-11-19 13:49:09 UTC]     0B media/
HTTP 200
```

Web console: `http://localhost:9001`, login `minio` / `minio12345`.

### Important terms

| Term | Meaning |
|---|---|
| Object storage | Whole blobs under keys in buckets, over HTTP. PUT / GET / DELETE. |
| Bucket / key | The named container / the object's name inside it. |
| S3 API | Amazon's object-storage HTTP API; the de-facto standard MinIO speaks. |
| Registry in an image ref | `cgr.dev/chainguard/minio` — anything before the first `/` with dots is a registry; none means Docker Hub. |
| Run-once container (job) | Does one task, exits. `Exited (0)` = success. |
| Idempotent | Safe to run again; `mc mb --ignore-existing` makes the bootstrap so. |
| `mc alias set` | Saves a server + credentials under a short name for later `mc` commands. |
| `/minio/health/live` | MinIO's liveness endpoint; answers 200 — future healthcheck material. |

### Common mistakes

- **Reading `Exited (0)` as a crash.** 0 = finished with no error. Jobs are supposed to exit.
  (`docker compose ps` hides exited containers; use `ps -a`.)
- **Trusting `depends_on` for readiness.** It orders creation only; the bootstrap's first
  `connection refused` proves it. The retry loop (or, later, a healthcheck condition) is the
  fix.
- **Putting a restart policy on a job.** `unless-stopped` would resurrect the finished
  bootstrap forever.
- **Pinning dogma without context.** The file pins everything it can; the one `:latest` is a
  recorded, commented trade-off forced by upstream, not laziness.

### Where it is used

- The media/upload phases store files in bucket `media` through the S3 SDK.
- The health endpoint joins Step 4's healthchecks; the bootstrap is Step 4's `restart: no`
  exception.
- The sourcing lesson returns every time an upstream changes licensing — a recurring 2020s
  event.

### Link to the task

- Step 3.6, word for word: "Add MinIO plus an `mc` bootstrap container — this extra container
  runs once at startup to create your first bucket, then exits. That exit is normal." Done,
  with the exit visible as `Exited (0)`.
- New-words table: **MinIO** and **`mc`** entries are this lesson; **Restart policy**'s
  "exactly right for a job that is supposed to run once and exit" meets its example here.

### Do it yourself

1. Add the `minio:` and `minio-init:` blocks above to the compose file, comments included.
2. Start and inspect **with `-a`**:

   ```bash
   docker compose -f deploy/compose/docker-compose.infra.yml up -d
   docker compose -f deploy/compose/docker-compose.infra.yml ps -a
   docker compose -f deploy/compose/docker-compose.infra.yml logs minio-init
   ```

   Expect `infra-minio-1` Up, `infra-minio-init-1` **Exited (0)**, and the log sequence above
   (the `connection refused` + `waiting for minio...` lines are the loop working, not a bug).
   Failure modes: `Exited (1)` → read the logs; script credentials not matching the `minio`
   env is the usual cause. Pull `Forbidden`/`401` → image reference typo; copy exactly.
3. Prove bucket and API:

   ```bash
   docker run --rm --network infra_default --entrypoint /bin/sh bitnamilegacy/minio-client:2025.7.21 \
     -c "mc alias set local http://minio:9000 minio minio12345 >/dev/null && mc ls local"
   curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:9000/minio/health/live
   ```

   Expect a `media/` row and `HTTP 200`. Then open `http://localhost:9001` and log in.

**Done when:** `ps -a` shows minio Up and minio-init `Exited (0)`, and you can explain why
that exit is a success.

### Summary for revision

- Object storage = blobs under keys in buckets over HTTP; S3 API = the standard; MinIO speaks
  it locally; AWS SDK against `localhost:9000`, prod swap = endpoint + credentials only.
- MinIO withdrew public images in 2025: Hub deleted, quay private (401), dl.min.io 410.
  Chose Chainguard rebuild (current, but `:latest`-only) + bitnamilegacy `mc` (frozen, pinned).
- Bootstrap = job: sh -c script, retry loop (depends_on ≠ readiness), idempotent `mb
  --ignore-existing`, exits 0. `ps -a` to see it; `restart: no` stays in Step 4.

### Check questions, with answers

**Question 1.** `docker compose ps` doesn't show `minio-init` at all, and `ps -a` shows it
`Exited (0)`. Someone calls that a crashed container. What do you tell them?

**Answer.** Exit code 0 means "finished with no error" — this container is a job whose whole
purpose is to run its script and end. The logs close with `bucket 'media' ready`, the proof of
success. Plain `ps` hides exited containers, which is why it "disappeared"; `ps -a` shows
everything. A crash would be a non-zero code — `Exited (1)` — with an error in the logs.

**Question 2.** The bootstrap has `depends_on: - minio` *and* a retry loop. Why isn't
`depends_on` alone enough — and what did the first log line of the real run prove?

**Answer.** Plain `depends_on` only makes Compose create and start MinIO's container first; it
says nothing about MinIO being ready to accept connections. The first log line —
`dial tcp 172.18.0.6:9000: connect: connection refused` — is that gap caught live: MinIO's
container existed, the process was starting, but nothing was listening yet. The `until` loop
retried once a second until MinIO answered, then did its work.

**Question 3.** In Step 4 every long-running service gets `restart: unless-stopped`, but
`minio-init` keeps `restart: no`. Concretely, what would happen with `unless-stopped` on it?

**Answer.** `unless-stopped` restarts a container whenever it exits — regardless of exit code —
unless a human stopped it. The bootstrap *always* exits when done, so Docker would start it
again: alias set, `mb --ignore-existing` (no-op), exit, restart... forever. An infinite loop
of a finished job: wasted CPU, noisy logs, a container forever flapping in `docker ps`. A job
that is supposed to run once and exit must keep the default `restart: no`.

**Question 4.** The Java code will talk to MinIO using the official AWS S3 library. Why does
that work, and what — exactly — changes in production against real S3?

**Answer.** MinIO implements S3's HTTP API precisely, and the AWS SDK only cares that the
server at the configured endpoint speaks that protocol. Locally the SDK is pointed at
`http://localhost:9000` with the dev credentials; in production the endpoint becomes the real
S3 service with real credentials (and possibly different bucket names) — configuration
changes, zero code changes. That dev-prod parity is the entire reason MinIO is in the stack.

### Your doubts

None in this lesson.

---

## Lesson 8 — Step 3 completes: Mailpit, and the six-service milestone

### Definition

**SMTP** is the decades-old protocol programs use to hand an email to a mail server.
**Mailpit** is a fake SMTP server: it accepts every message on port 1025, **delivers none of
them**, and shows everything in a web inbox (plus a REST API) on port 8025.

### Why it is needed

The app will send email — signup confirmations, password resets, receipts. Pointing dev at a
real mail server means: one day a test spams real-looking addresses of real people; provider
credentials are needed before the first feature; every test waits on real delivery. Mailpit
removes all three. Same dev-prod parity trick as MinIO: the app speaks the real protocol;
configuration decides whether the other end is a harmless catcher or a real provider.

### How it works, step by step

1. The smallest block in the file:

   ```yaml
     mailpit:
       image: axllent/mailpit:v1.34.0
       ports:
         - "1025:1025" # SMTP - where the app hands over email
         - "8025:8025" # web UI + API - where you read it
   ```

2. Two ports, two audiences: **programs** speak SMTP into 1025; **humans and tests** read over
   HTTP from 8025 (browser UI and REST API).
3. No environment block — like Redis, born ready. Idles at ~7.5 MiB, lightest in the stack.

### Example — real commands, real output from this machine

**A real email through the fake server** (`curl` speaks SMTP):

```bash
printf 'Subject: Welcome to the project\nFrom: app@dev.local\nTo: pavan@dev.local\n\nYour account is ready.\n' \
  | curl -s --url smtp://localhost:1025 --mail-from app@dev.local --mail-rcpt pavan@dev.local --upload-file -
curl -s http://localhost:8025/api/v1/messages | jq '{total: .total, subject: .messages[0].Subject}'
```

```text
{
  "total": 1,
  "subject": "Welcome to the project"
}
```

In SMTP, out HTTP; `http://localhost:8025` shows it rendered like a real inbox.

**The Step 3 milestone — one file, one command, all six:**

```text
NAME                      IMAGE                                   STATUS
infra-kafka-1             apache/kafka:4.4.0                      Up 7 hours
infra-mailpit-1           axllent/mailpit:v1.34.0                 Up 23 seconds (healthy)
infra-minio-1             cgr.dev/chainguard/minio:latest         Up 3 minutes
infra-minio-init-1        bitnamilegacy/minio-client:2025.7.21    Exited (0) 23 seconds ago
infra-postgres-1          postgres:17-alpine                      Up 7 hours
infra-redis-1             redis:7-alpine                          Up 7 hours
infra-schema-registry-1   confluentinc/cp-schema-registry:8.4.1   Up 7 hours
```

Total memory ≈ 1.1 GiB of the VM's 7.7. Two spotted details that feed Step 4:

1. **Mailpit alone says `(healthy)`** — its image author baked a healthcheck into the image,
   so Docker probes it and reports a verdict. The other five have no healthcheck: plain `Up`
   guarantees only "the main process exists".
2. **`minio-init` re-ran on this `up`** — Compose re-runs job containers on every `up`; the
   `--ignore-existing` idempotency is what makes that a harmless no-op.

### Important terms

| Term | Meaning |
|---|---|
| SMTP | The protocol for handing email to a mail server. Port 1025 here. |
| Mailpit | Fake SMTP server: catches everything, delivers nothing, shows all on 8025. |
| `(healthy)` | Docker's verdict from a defined healthcheck — Mailpit's came baked into its image. |
| Dev-prod parity | Speak the real protocol locally against a harmless stand-in; swap endpoints in prod. |

### Common mistakes

- **Testing against a real mail account** — works until a bulk test emails real people.
- **Sending to 8025 or reading from 1025** — each port has one job and one protocol.
- **Assuming caught mail survives restarts** — by default Mailpit keeps messages in memory
  (a volume plus its database setting changes that; Step 4 touches this).

### Where it is used

- Every later phase that sends email points the app's SMTP settings at `localhost:1025`.
- Tests can assert "exactly one email was sent, with this subject" via the 8025 REST API.

### Link to the task

- Step 3.7, word for word: "Add Mailpit — the fake SMTP server that catches every email your
  app sends and shows it on a local web page." Done, proven with a real message.
- Step 3's overall "you know it worked when" — one `up -d`, six services running, `mc`
  bootstrap exited 0 — verified in the table above.

### Do it yourself

1. Add the `mailpit:` block as the last service in the compose file.
2. Start, send, verify:

   ```bash
   docker compose -f deploy/compose/docker-compose.infra.yml up -d
   printf 'Subject: Welcome\nFrom: app@dev.local\nTo: you@dev.local\n\nHello.\n' | \
     curl -s --url smtp://localhost:1025 --mail-from app@dev.local --mail-rcpt you@dev.local --upload-file -
   curl -s http://localhost:8025/api/v1/messages | jq '.total, .messages[0].Subject'
   ```

   Expect `1` and `"Welcome"`; then open `http://localhost:8025`.
   Failure modes: `connection refused` on 1025 → container still starting, retry;
   `port is already allocated` → `docker ps` finds the squatter.
3. Milestone check: `docker compose -f deploy/compose/docker-compose.infra.yml ps -a` —
   all six `Up`, `minio-init` `Exited (0)`.

**Done when:** the email round-trip works and `ps -a` matches the milestone table.

### Summary for revision

- Mailpit = fake SMTP: in on 1025, never delivered, read on 8025 (UI + API). ~7.5 MiB.
- Dev-prod parity again: real protocol, harmless local endpoint.
- Step 3 done: six services, one command, bootstrap exits 0, ~1.1 GiB total.
- Spotted for Step 4: only Mailpit reports `(healthy)`; jobs re-run on every `up`.

### Check questions, with answers

**Question 1.** Why a fake SMTP server instead of a real mail account reserved for testing?
Name the three problems Mailpit removes.

**Answer.** (1) Accidental real delivery: one bulk test against realistic data and real people
get spammed — Mailpit delivers nothing, ever. (2) Credentials friction: a real account means
provider setup and secrets before the first feature — Mailpit needs none. (3) Speed and
inspection: real delivery is slow and hard to assert on — Mailpit is instant, offline, and
exposes every caught message through a UI and a REST API tests can query.

**Question 2.** Ports 1025 and 8025 serve two different audiences. Who talks to each, and in
which protocol?

**Answer.** Port 1025 is for **programs**: the app (and the `curl` demo) hands email over in
SMTP, exactly as it would to a real mail server. Port 8025 is for **humans and tests**: a web
inbox in the browser and a REST API (`/api/v1/messages`) over HTTP for reading and asserting
on what was caught.

**Question 3.** In the status table, Mailpit alone says `(healthy)`. Where did that verdict
come from, what do the plain `Up`s of the other five actually guarantee — and why is that gap
about to matter?

**Answer.** The Mailpit image ships a healthcheck instruction baked in by its author, so
Docker runs that probe and reports `(healthy)`. The other five services have no healthcheck
defined anywhere, so their `Up` means only "the main process exists" — nothing about accepting
connections. The gap matters because Step 4 adds healthchecks to every service and Step 5
builds start ordering on them (`depends_on` + `condition: service_healthy`), which is what
finally makes `make up` mean *ready*, not merely *started*.

### Your doubts

None in this lesson.

---

## Lesson 9 — Step 4 begins: named volumes

### Definition

A **named volume** is a folder Docker manages *outside* any container, mounted into a
container at a path you choose. The container sees a normal folder; the data lives in Docker's
keeping. Delete the container — the volume stays. A new container mounting the same volume
finds the data simply there. The volume belongs to the stack, not to any one container.

### Why it is needed

Every file a container writes lands in its **writable layer**, and the writable layer is
deleted with the container. `docker compose down` deletes containers — so until this lesson,
`down` was throwing the database away. A daily-driver stack must survive teardown.

### How it works, step by step

1. Mount a named volume in each stateful service, **at the path where that program actually
   writes** — a volume at the wrong path protects an empty folder while the real data dies.
2. Declare the names in a top-level `volumes:` block. Compose prefixes them with the project
   name: `infra_pg-data`, `infra_redis-data`, ...
3. The decisions for this stack:

   | Service | Volume → path | Worth knowing |
   |---|---|---|
   | postgres | `pg-data:/var/lib/postgresql/data` | Postgres's standard data directory |
   | redis | `redis-data:/data` | where Redis writes its snapshot file |
   | kafka | `kafka-data:/var/lib/kafka/data` | plus `KAFKA_LOG_DIRS` — see below |
   | schema-registry | **none, on purpose** | stateless; its truth is Kafka's `_schemas` topic |
   | minio | `minio-data:/data` | the `/data` its `server /data` command names |
   | mailpit | `mailpit-data:/data` + `MP_DATABASE: /data/mailpit.db` | default is in-memory |

4. **Kafka needed an env var too.** The `apache/kafka` image's default data directory is in
   `/tmp` — so `KAFKA_LOG_DIRS: /var/lib/kafka/data` *moves* Kafka's writing onto the volume.
   The fixed `CLUSTER_ID` from the Kafka lesson pays off here: data on the volume carries that
   identity stamp, and a restart under a different ID would refuse to start.
5. **Redis persists via snapshots.** It is a RAM store that periodically writes
   `/data/dump.rdb`. A key set seconds before a crash may miss the last snapshot and die —
   acceptable for cache duty; truth lives in Postgres. (`redis-cli save` forces a snapshot.)
6. New meaning of commands: `down` = containers and network removed, **volumes stay** (safe
   daily). `down -v` = volumes deleted too — the full wipe that becomes `make reset` in
   Step 10.

### Example — real commands, real output from this machine

**Part A — the loss, demonstrated with no volumes:**

```text
app=# create table customers(...); insert ... ('Asha'),('Ravi');
redis> set important-counter 42        -> OK
$ docker compose -f deploy/compose/docker-compose.infra.yml down
$ docker compose -f deploy/compose/docker-compose.infra.yml up -d

$ docker exec infra-postgres-1 psql -U postgres -d app -c "select * from customers;"
ERROR:  relation "customers" does not exist
$ docker exec infra-redis-1 redis-cli get important-counter
                                        <- empty
```

**Part B — volumes added; Docker now manages five folders:**

```text
$ docker volume ls
infra_kafka-data  infra_mailpit-data  infra_minio-data  infra_pg-data  infra_redis-data
```

**Part C — same experiment, opposite result:**

```text
$ docker exec infra-postgres-1 psql -U postgres -d app -c "select * from customers;"
 id | name
----+------
  1 | Asha
  2 | Ravi

$ docker exec infra-redis-1 redis-cli get important-counter
42

$ docker exec infra-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
__consumer_offsets
_schemas
```

`_schemas` surviving is the Schema Registry lesson's prediction coming true: Kafka's volume is
what protects the schemas; the stateless registry needed nothing.

### Important terms

| Term | Meaning |
|---|---|
| Named volume | Docker-managed folder outside containers; outlives them. |
| Writable layer | Per-container scratch storage; dies with the container. |
| Mount path | Where the volume appears inside the container — must match where the program writes. |
| Top-level `volumes:` | Declares volume names; Compose prefixes with the project name. |
| `KAFKA_LOG_DIRS` | Points Kafka's data (messages + KRaft metadata) at the volume, away from `/tmp`. |
| `down` vs `down -v` | Remove containers+network vs also delete volumes (full wipe). |
| `redis-cli save` | Force a Redis snapshot to `/data/dump.rdb` now. |

### Common mistakes

- **Mounting a volume at a path the program doesn't use** — Kafka's `/tmp` default is the
  textbook case; the volume guards an empty folder while data dies.
- **Expecting every Redis write to survive** — only what made it into the last snapshot does.
- **"Adding a volume for safety" to stateless services** — the registry's state lives in
  Kafka; know where state actually is before protecting it.
- **Using `down -v` casually** — that flag is the difference between safe and scorched-earth.

### Where it is used

- Every later phase trusts `make down && make up` not to lose the database.
- `make reset` (Step 10) wraps `down -v` — the deliberate clean slate.
- The volume-vs-bind-mount rule (Step 7) builds directly on this lesson.

### Link to the task

- Step 4.1, word for word: "Give every service a named volume for its data, so `docker compose
  down` does not throw your database away." Done — with the schema-registry exception
  documented, which the task's own Schema Registry lesson supports (its state is in Kafka).
- Step 4's "you know it worked when" first clause — create a table, `down`, `up -d`, table
  still there — proven above with real output.

### Do it yourself

1. Run the loss first (it makes the fix mean something): create a table, `down`, `up -d`,
   select → expect, genuinely, `ERROR: relation "customers" does not exist`.
2. Edit the compose file exactly as in this repo: five volume mounts, `KAFKA_LOG_DIRS`,
   `MP_DATABASE`, the registry's "no volume on purpose" comment, top-level `volumes:` block.
3. `up -d`, then `docker volume ls` → expect five `infra_*` volumes.
4. Repeat the experiment with `redis-cli save` before `down` → expect the row and the counter
   to survive.

**Done when:** the same `down`/`up` cycle that destroyed the table now preserves it, and you
can name which service got no volume and why.

### Summary for revision

- Writable layer dies with the container; named volumes live outside and survive.
- Mount where the program writes: Postgres `/var/lib/postgresql/data`, Redis `/data`, Kafka
  `/var/lib/kafka/data` (moved by `KAFKA_LOG_DIRS`), MinIO `/data`, Mailpit `/data` + env.
- Registry: no volume, stateless, truth in `_schemas` on Kafka's volume.
- Redis survives via snapshots, not every write. `down` safe; `down -v` = wipe = future reset.

### Check questions, with answers

**Question 1.** After this lesson, `down` then `up`: which survive — a Postgres row, a Redis
key set one second before the `down` (no `save`), a registered schema, a caught email?

**Answer.** The Postgres row survives — Postgres writes through to its data directory on the
volume. The Redis key probably **dies**: Redis persists by periodic snapshot, and a key set one
second earlier almost certainly isn't in the last `dump.rdb` on the volume — volumes preserve
only what was written to them. The schema survives — it lives in the `_schemas` topic on
*Kafka's* volume. The email survives — `MP_DATABASE` points Mailpit at a database file on its
volume (before that env var, Mailpit kept mail in memory and it died with the container).

**Question 2.** Kafka needed both a `volumes:` entry and a `KAFKA_LOG_DIRS` env var. Why
wasn't the volume alone enough?

**Answer.** A volume protects a *path*. The `apache/kafka` image writes its data to a folder
under `/tmp` by default, so a volume mounted at `/var/lib/kafka/data` would guard an empty
directory while messages and KRaft metadata kept dying in `/tmp` with each container.
`KAFKA_LOG_DIRS` redirects Kafka's writing to the mounted path, making the volume actually
cover the data.

**Question 3.** A teammate adds a volume to `schema-registry` "for safety". What did they
misunderstand?

**Answer.** Where the registry's state lives. It stores every schema in Kafka's `_schemas`
topic and holds nothing durable of its own — it is stateless and rebuilds its memory by
replaying that topic at startup. Their volume would faithfully preserve an empty folder. The
real protection for schemas is Kafka's `kafka-data` volume, which already exists.

**Question 4.** What is now the difference between `docker compose down` and
`docker compose down -v` — and which will `make reset` wrap in Step 10?

**Answer.** `down` removes containers and the network but **keeps volumes** — data survives;
safe for daily use. `down -v` additionally **deletes the volumes** — database, Kafka topics,
buckets, caught mail all gone; a deliberate clean slate. `make reset` wraps `down -v` (down
**and** wipe volumes), while `make down` wraps the safe form.

### Your doubts

None in this lesson.

---

## Lesson 10 — Step 4 completes: healthchecks, the named network, restart policies

### Definition

A **healthcheck** is a command Docker runs inside a container on a schedule; exit 0 = healthy.
Docker tracks a verdict — `(health: starting)` → `(healthy)` / `(unhealthy)` — which is the
difference between "the process exists" and "the service accepts work".

A **fixed, named network** gives the stack's one network a stable name (`infra-net`)
independent of project naming, so anything can join it by name; containers keep reaching each
other by service name.

A **restart policy** tells Docker what to do when a container exits. `unless-stopped`: bring
it back after a crash, daemon restart, or reboot — unless you stopped it yourself. Default
`no`: never — correct for run-once jobs.

### Why it is needed

**The incident that found us (real):** during the volume work, Schema Registry crash-raced a
not-yet-ready Kafka — `Exited (1)`, logs full of `Node -1 disconnected`. Worse, the race made
Kafka **auto-create** `_schemas` with default settings instead of the registry's required
compacted config:

```text
StoreInitializationException: The retention policy of the schema topic _schemas is
incorrect. Expected cleanup.policy to be 'compact' but it is delete
```

...and the new volume **preserved the damage** across restarts. Two permanent lessons: a
readiness race can corrupt durable state, not just crash-loop; and persistence preserves
mistakes as loyally as data. (Fix: delete the empty poisoned topic; a healthy registry
recreates it correctly — `cleanup.policy=compact` confirmed.) Step 4's machinery exists to
make this class of failure impossible — Step 5 wires it into start ordering.

### How it works, step by step

1. **Healthcheck anatomy** (Postgres, the task's exact shape):

   ```yaml
   healthcheck:
     test: ["CMD-SHELL", "pg_isready -U postgres"]  # probe, run inside the container
     interval: 5s     # how often
     timeout: 3s      # max probe duration before it counts as failed
     retries: 10      # consecutive failures before verdict flips unhealthy
   ```

   `start_period: 15s` (used on kafka, schema-registry) is a startup grace window — failures
   inside it don't count against `retries`.
2. **Each image gets a probe it can actually run** — archaeology was required:

   | Service | Probe | Story |
   |---|---|---|
   | postgres | `pg_isready -U postgres` | official readiness tool, task-verbatim |
   | redis | `redis-cli ping` | `PONG` only when serving |
   | kafka | `/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list` | full path (image's scripts not on PATH); JVM tool → `timeout: 10s`, `start_period: 15s` |
   | schema-registry | `bash -c 'echo > /dev/tcp/localhost/8081'` | Confluent stripped `curl`; bash `/dev/tcp` proves the port accepts connections |
   | minio | `mc alias set health ... && mc ready health` | chainguard image has no curl/wget but bundles `mc` |
   | mailpit | none written | baked into the image by its author |

3. **Network**: rename the default network instead of inventing per-service ones:

   ```yaml
   networks:
     default:
       name: infra-net
   ```

4. **Restart**: `restart: unless-stopped` on all six long-runners;
   `restart: "no"` on `minio-init` — **quoted**, because unquoted `no` is YAML's boolean
   `false`, not the string Docker expects. A finished job must not be resurrected.

### Example — real commands, real output from this machine

**Verdicts forming:**

```text
immediately after up -d:                 35 seconds later:
infra-kafka-1    (health: starting)  →   Up 42 seconds (healthy)
infra-postgres-1 (health: starting)  →   Up 43 seconds (healthy)
infra-redis-1    (health: starting)  →   Up 42 seconds (healthy)
infra-schema-... (health: starting)  →   Up 42 seconds (healthy)
infra-minio-1    (health: starting)  →   Up 42 seconds (healthy)
infra-mailpit-1  (health: starting)  →   Up 43 seconds (healthy)
infra-minio-init-1                       Exited (0)
```

```text
$ docker network ls | grep infra
infra-net   bridge
```

**The crash test, two parts, both instructive:**

```text
$ docker kill infra-redis-1            # kill via the Docker API
$ docker ps -a | grep redis
infra-redis-1   Exited (137) 16 seconds ago     <- NOT restarted

$ docker start infra-redis-1
$ docker exec infra-redis-1 redis-cli shutdown nosave   # process dies from inside
$ docker ps | grep redis
infra-redis-1   Up 3 seconds (health: starting)         <- came back alone
```

`docker kill` counts as "you stopped it yourself" — the *unless-stopped* clause — so no
resurrection. A process dying on its own is a crash, and the policy revives it.

### Important terms

| Term | Meaning |
|---|---|
| Healthcheck verdict | `starting` → `healthy`/`unhealthy`; shown in `docker ps`. |
| `test` / `interval` / `timeout` / `retries` | The probe; how often; max duration; failures to flip. |
| `start_period` | Grace window at startup; failures inside it don't count. |
| `CMD-SHELL` | Run the probe through the container's shell. |
| `/dev/tcp/host/port` | bash built-in TCP connect — probe for images without curl/wget. |
| `infra-net` | The stack's fixed network name; join-able by anything, stable forever. |
| `unless-stopped` | Restart after crash/daemon-restart/reboot, except after a manual stop/kill. |
| `restart: "no"` | The quoted string; unquoted `no` is YAML boolean `false`. |

### Common mistakes

- **Assuming every image has `curl`** — modern slim images often have neither curl nor wget;
  find what the image *does* have (`bash /dev/tcp`, a bundled client, a built-in subcommand).
- **A tight `timeout` on a JVM probe** — `kafka-topics.sh` boots a whole JVM; 3 seconds can
  misjudge a perfectly healthy broker as failing.
- **Unquoted `restart: no`** — YAML turns it into boolean `false`.
- **Expecting `unless-stopped` to undo `docker kill`** — API stops/kills are manual stops.
- **Forgetting the exception** — a restart policy on a run-once job resurrects it forever.

### Where it is used

- Step 5 wires `depends_on` to these verdicts (`condition: service_healthy`) — start ordering
  by *readiness*, which is what finally makes the registry race impossible.
- `make up` (Step 10) will mean "ready", not "started", because these checks exist.
- The app joins `infra-net` by name in later phases; Grafana/Prometheus (Phase 11) and nginx
  (Phase 10) land on the same named network.

### Link to the task

- Step 4.2–4.5, word for word: healthcheck for every service; one fixed, explicitly named
  network; `restart: unless-stopped` on long-runners; `mc` bootstrap stays `restart: no`. All
  applied and proven above.
- Step 4's "you know it worked when": table survives `down`/`up` (Lesson 9), `docker ps` shows
  `(healthy)` next to each long-running service (above), and after restarting Docker itself
  everything except `mc` comes back — the kill-test shows the mechanism; the full
  Docker-restart run is in the Do-it-yourself.

### Do it yourself

1. Apply all three settings exactly as in this repo's compose file: five `healthcheck:`
   blocks, `restart: unless-stopped` ×6, `restart: "no"` on `minio-init`, and the
   `networks:` block naming the default network `infra-net`.
2. Recreate and watch verdicts:

   ```bash
   docker compose -f deploy/compose/docker-compose.infra.yml up -d
   docker compose -f deploy/compose/docker-compose.infra.yml ps     # (health: starting)
   sleep 35
   docker compose -f deploy/compose/docker-compose.infra.yml ps -a  # (healthy) x6, init Exited (0)
   docker network ls | grep infra                                   # infra-net
   ```

3. Crash test:

   ```bash
   docker exec infra-redis-1 redis-cli shutdown nosave
   sleep 4
   docker ps | grep redis    # expect: Up a few seconds - back on its own
   ```

4. Full Step 4 check: quit and reopen OrbStack (restart Docker itself), then `docker ps` —
   everything except `minio-init` returns alone and turns `(healthy)`.

**Done when:** all six show `(healthy)`, Lesson 9's table still exists, and a self-dying redis
comes back without you.

### Summary for revision

- Healthcheck = scheduled in-container probe; verdict `starting/healthy/unhealthy`; anatomy:
  test, interval, timeout, retries (+ start_period grace).
- Probes must fit the image: pg_isready; redis-cli ping; full-path kafka-topics (JVM → long
  timeout); bash /dev/tcp for curl-less registry; bundled mc for MinIO; Mailpit's came baked.
- One network, fixed name `infra-net`; service-name DNS unchanged.
- `unless-stopped` on services; quoted `"no"` on the job. API kill = manual stop (no revival);
  death from inside = crash (revived).
- The registry incident: races can corrupt durable state; volumes preserve mistakes too.

### Check questions, with answers

**Question 1.** The registry incident left `_schemas` wrongly configured, and the volume
preserved the damage. One sentence each: what does this say about readiness races, and about
volumes?

**Answer.** Races: a service starting against an unready dependency doesn't just crash — its
half-finished initialization can leave *wrong durable state* behind (here, an auto-created
topic with the wrong cleanup policy). Volumes: they persist whatever was written, mistakes
included — persistence is not correctness, so broken state now survives restarts exactly as
loyally as good data.

**Question 2.** A healthcheck has `interval: 5s`, `timeout: 3s`, `retries: 10`,
`start_period: 15s`. What does each number do — and why did Kafka's check get a 10-second
timeout while Postgres kept 3?

**Answer.** Every 5 seconds (`interval`) Docker runs the probe; a probe that hasn't answered
within `timeout` counts as failed; 10 consecutive failures (`retries`) flip the verdict to
unhealthy; failures during the first 15 seconds (`start_period`) are forgiven as startup
grace. Kafka's probe is `kafka-topics.sh` — a tool that boots an entire JVM before doing any
work, which alone can take seconds, so a 3-second timeout would misjudge a healthy broker.
`pg_isready` is a tiny native program that answers in milliseconds; 3 seconds is generous.

**Question 3.** `docker kill` left redis dead, but `redis-cli shutdown` saw it resurrected.
What distinction is `unless-stopped` drawing?

**Answer.** Who ended the container. `docker kill`/`docker stop` go through the Docker API —
that is *you* stopping it, and `unless-stopped` honors a manual stop by staying down. With
`redis-cli shutdown` the process ended *from the inside*, indistinguishable from a crash, so
the policy's job is exactly to bring it back — which it did in about 3 seconds, unasked.

**Question 4.** Why is `restart: "no"` quoted — and what would the unquoted version mean?

**Answer.** In YAML (1.1, which Compose follows here), bare `no` parses as the **boolean
`false`**, not the two-letter string — so `restart: no` would hand Docker a boolean where it
expects a policy name. Quoting forces the string `"no"`, the actual policy meaning "never
restart". (Same family of traps: `yes`, `on`, `off` — quote them when you mean text.)

### Your doubts

None in this lesson.

---

## Lesson 11 — Step 5: `depends_on` that waits for *ready*, and `up -d --wait`

### Definition

`depends_on` with **`condition: service_healthy`** tells Compose: do not even *start* this
container until that dependency's healthcheck passes. The three conditions:

| Condition | Meaning | Right when |
|---|---|---|
| `service_started` | old behavior: container exists | dependency's readiness doesn't matter, or the dependent retries robustly itself |
| `service_healthy` | wait until the healthcheck passes | registry → kafka; minio-init → minio |
| `service_completed_successfully` | wait until a job exits 0 | anything needing `minio-init`'s bucket to already exist |

**`docker compose up -d --wait`** blocks until every service is running **and healthy** (jobs
exiting 0 are fine) — the ingredient that lets `make up` honestly mean "ready".

### Why it is needed

Step 4 gave Docker the *knowledge* of readiness (verdicts in `docker ps`); ordering wasn't
*using* it — plain `depends_on` still meant "create first", nothing more. The registry
incident (crash-race, poisoned `_schemas`) lived exactly in that gap. One condition line
closes it: the race stops being survivable and becomes **structurally impossible**.

### How it works, step by step

1. Short form becomes a map, because each dependency now carries a condition:

   ```yaml
     schema-registry:
       depends_on:
         kafka:
           condition: service_healthy # do not even start me until Kafka's healthcheck passes
   ```

2. `minio-init` gets the same (`minio: condition: service_healthy`) — and its retry loop
   stays. Layers do different jobs: the condition guarantees clean cold-start ordering; the
   loop also covers a MinIO hiccuping *after* the job started, and documents the job's
   patience in the script itself.
3. Compose validation: a `service_healthy` condition pointing at a service with **no
   healthcheck** is refused outright — the condition would have nothing to read.
4. `up -d` (plain) returns once containers are started (dependencies honored); `up -d --wait`
   returns only at all-healthy. A leaf service like the registry can still be initializing
   after plain `up -d` returns — scripts that poke it immediately can fail without anything
   being wrong.

### Example — real commands, real output from this machine

The full ceremony — volumes destroyed, cold start, timed:

```bash
docker compose -f deploy/compose/docker-compose.infra.yml down -v
docker compose -f deploy/compose/docker-compose.infra.yml up -d --wait
```

```text
 Container infra-redis-1 Healthy
 Container infra-kafka-1 Healthy
 Container infra-postgres-1 Healthy
container infra-minio-init-1 exited (0)
cold start to ALL-HEALTHY took: 8 seconds
```

The log narrates the ordering: Kafka turns `Healthy`, *then* the registry starts (its
container was seconds younger than the rest). And the service that crashed twice before,
under the same cold-start conditions:

```text
infra-schema-registry-1   Up 36 seconds (healthy)
RestartCount: 0
'Node -1 disconnected' lines in log: 0
$ curl -s http://localhost:8081/subjects
[]
```

Eight seconds from wiped volumes (warm images — only the very first run pulls).

### Important terms

| Term | Meaning |
|---|---|
| `condition: service_healthy` | Start the dependent only after the dependency's healthcheck passes. |
| `service_completed_successfully` | Start only after a job container exits 0. |
| `up -d --wait` | Block until everything is running and healthy. `make up`'s core. |
| RestartCount | `docker inspect -f '{{.RestartCount}}'` — 0 means a clean, crash-free start. |

### Common mistakes

- **Removing the condition because "it works anyway"** — with `restart: unless-stopped` the
  registry *eventually* comes up even when racing, but with crash scars (restarts, error spam,
  corruption risk). Eventual success is not a clean start.
- **Expecting plain `up -d` to mean ready** — it returns at "started"; use `--wait`.
- **`service_healthy` against a healthcheck-less service** — Compose refuses the file.
- **Testing once** — Step 5's requirement is "green every single time"; repeat the ceremony.

### Where it is used

- `make up` (Step 10) wraps `up -d --wait`; the under-90-seconds done-when is measured by it.
- Step 6 deletes the condition on purpose to watch the failure signature, then restores it.
- Every later phase that adds a dependent service (app → postgres/kafka) reuses this pattern.

### Link to the task

- Step 5, word for word: healthcheck shapes for postgres/redis/kafka (done in Step 4, kafka's
  with the `/opt/kafka/bin/` path correction), and `schema-registry` depending on `kafka`
  with `condition: service_healthy` — exactly the block above.
- Step 5's "you know it worked when": full teardown and cold start comes up green every
  single time, no service crash-looping while it waits — proven with RestartCount 0 and a
  zero-error log.

### Do it yourself

1. Upgrade both `depends_on` blocks to the long form with `condition: service_healthy`
   (registry → kafka, minio-init → minio), exactly as in this repo's file.
2. The full ceremony:

   ```bash
   docker compose -f deploy/compose/docker-compose.infra.yml down -v
   docker compose -f deploy/compose/docker-compose.infra.yml up -d --wait
   docker compose -f deploy/compose/docker-compose.infra.yml ps -a
   ```

   Expect `up --wait` to return only at healthy; `infra-kafka-1 Healthy` printed before the
   registry starts; final table all `(healthy)` + init `Exited (0)`.
3. Prove the clean start:

   ```bash
   docker inspect -f '{{.RestartCount}}' infra-schema-registry-1                      # 0
   docker compose -f deploy/compose/docker-compose.infra.yml logs schema-registry \
     | grep -c 'Node -1 disconnected'                                                 # 0
   ```

4. Repeat the ceremony two or three times — the requirement is every-single-time green.

**Done when:** repeated `down -v` → `up -d --wait` cycles come up all-healthy with the
registry's RestartCount at 0.

### Summary for revision

- Step 4 = knowledge (verdicts); Step 5 = ordering uses it: `condition: service_healthy`.
- Three conditions: started / healthy / completed_successfully (jobs).
- Belt and braces: condition for cold-start order, retry loop for mid-run hiccups.
- `up -d` returns at started; `up -d --wait` at all-healthy — the future `make up`.
- Proof standard: RestartCount 0, zero error spam, repeatable.

### Check questions, with answers

**Question 1.** Give one concrete example from this stack where each condition —
`service_started`, `service_healthy`, `service_completed_successfully` — would be the right
choice.

**Answer.** `service_healthy`: the two real uses — schema-registry must not start before
Kafka's healthcheck passes; minio-init must not run before MinIO is ready.
`service_completed_successfully`: a future job or app that needs the `media` bucket to exist
would depend on `minio-init` with this condition — "start me only after the bucket job
finished OK". `service_started`: acceptable where the dependent handles readiness itself —
e.g. if minio-init had only its retry loop, mere `service_started` on MinIO would suffice,
since the loop absorbs unreadiness (we wear belt and braces instead).

**Question 2.** `minio-init` now waits for a healthy MinIO *and* still has its retry loop.
What distinct failure does each layer cover?

**Answer.** The condition covers **cold-start ordering**: the job container is not even
created-and-started until MinIO's healthcheck passes, so it can never race a booting MinIO.
The loop covers **what happens after the job has started**: a MinIO that restarts or briefly
refuses connections mid-run would fail a single `mc alias set`, but the loop retries until it
answers. One guards the start line; the other guards the race itself.

**Question 3.** A teammate runs plain `docker compose up -d` and immediately scripts against
Schema Registry on port 8081. The registry didn't crash — but the script may still fail. Why,
and which two tools from this lesson each fix it?

**Answer.** `up -d` returns when containers are *started* (dependencies honored) — but the
registry itself then needs ~10–15 seconds of Java startup before its API listens, so the
script can hit `connection refused` against a perfectly fine stack. Fix one: run
`up -d --wait`, which doesn't return until every service (registry included) is healthy. Fix
two: if the script itself ran as a container in the stack, give it
`depends_on: schema-registry: condition: service_healthy`, so Compose holds it until the
registry's verdict is green.

### Your doubts

None in this lesson.

---

## Lesson 12 — Break it on purpose: races, scars, and what "flaky" means (Step 6)

### Definition

A **race condition** between services is when the outcome of a start-up depends on *timing you
do not control*: whether the dependency happens to become ready before the dependent's patience
runs out. **Flaky** is the visible symptom — the same configuration sometimes works and
sometimes fails, with nothing changed in between. This lesson deletes the
`condition: service_healthy` line from the schema-registry on purpose, runs cold starts, and
studies exactly what comes back.

### Why it is needed

So you recognize this failure instantly in the wild. A service that crashes on start "for no
reason", works after a retry, and never fails when you watch it closely — that is the single
most common infrastructure bug there is. Having caused it, watched it, and fixed it once on
purpose means you will never waste an afternoon on it by accident.

### How it works, step by step

1. The registry's `depends_on` is downgraded to the bare form (`- kafka`), which only orders
   *creation*, not readiness. The registry now starts the moment Kafka's process exists —
   typically ~5–10 seconds before Kafka can actually answer.
2. During that gap the registry hammers `kafka:29092` and logs one `Node -1 disconnected`
   line per failed attempt. It has an internal patience budget: it retries for a while
   before giving up and crashing.
3. **If Kafka becomes ready inside the budget**, the registry connects and comes up green —
   but the failed attempts remain in its log as **scars**. It raced and *won*.
4. **If the gap outlasts the budget** (slower machine, cold images, fresh volume being
   formatted, heavy disk load), the registry dies with `Exited (1)` — and, worst case, its
   half-finished initialization tricks Kafka into auto-creating the `_schemas` topic with the
   wrong `cleanup.policy`, corruption that a named volume then faithfully preserves.
5. Which of 3 and 4 happens is decided by milliseconds. That is why three green test runs
   prove nothing: you cannot test a race away, only remove it structurally.

### Example — real commands, real output from this machine

The condition deleted, then three full ceremonies (`down -v` → `up -d` → wait 20s):

```
run 1:  infra-schema-registry-1  Up (healthy)   RestartCount: 0   disconnect-spam lines: 5
run 2:  infra-schema-registry-1  Up (healthy)   RestartCount: 0   disconnect-spam lines: 5
run 3:  infra-schema-registry-1  Up (healthy)   RestartCount: 0   disconnect-spam lines: 5
```

Three wins — and three sets of scars. Every run shows the registry failing five times against
an unready Kafka before squeaking through. The spam count was measured with:

```
docker compose -f deploy/compose/docker-compose.infra.yml logs schema-registry \
  | grep -c 'Node -1 disconnected'
```

The counterfactual needs no staging, because it happened **organically earlier in this very
task** (Lesson 10's incident): the same raced start-up, on the same machine, lost twice while
volumes were being formatted and every container recreated at once — `Exited (1)`, disconnect
lines in the hundreds, and a poisoned `_schemas` topic left behind. Same file, different hour,
opposite outcome. That *is* flakiness.

Condition restored, one more ceremony:

```
infra-schema-registry-1  Up 5 seconds (health: starting)
RestartCount: 0   spam: 0
```

Zero. Not "survived the race" — **never raced**. The registry was not allowed to start until
Kafka's healthcheck verdict was green, so its log contains not a single failed connection.

### The failure signature — recognize it in the wild

- **`docker ps`:** a dependent service `Restarting` or freshly `Exited (1)` while its
  dependency sits there `Up` — often `Up (healthy)` by the time you look, because the
  dependency finished booting *after* killing the dependent.
- **The dependent's logs:** repeated connection failures to a service that exists —
  `Node -1 disconnected`, `Connection refused`, `Broker may not be available` — ending either
  in recovery or one final fatal exception.
- **`docker inspect -f '{{.RestartCount}}' <container>`** greater than 0 on a stack that
  "came up fine".
- **The sneaky variant:** everything green *now*, but corrupted durable state left behind by
  a half-initialized loser of an earlier race (the wrongly-configured `_schemas` topic).
  The crash healed; the damage persisted in the volume.
- **The scar check, even on green days:** grep the dependent's log for connection-failure
  lines right after a cold start. Nonzero means you are racing and merely winning.
  Five lines tonight is tomorrow's outage.

### Important terms

| Term | Meaning |
|---|---|
| Race condition | Outcome depends on event timing you do not control |
| Flaky | Same setup, intermittent pass/fail — the symptom of a race |
| Scar | Failure evidence in logs of a service that ultimately recovered |
| Patience budget | How long a client retries its dependency before giving up and crashing |
| "Works on my machine" | A race your fast machine wins and slower machines (CI, laptops) lose |

### Common mistakes

- Concluding from green runs that an ordering guard is unnecessary. Wins still carry scars;
  check the logs, not just the status column.
- Fixing a race by adding `sleep 10` to a script. That just bets on a bigger head start —
  slower machines still lose. The structural fix is a readiness gate (`service_healthy`).
- Debugging the dependency ("is Kafka broken?") when the crash is in the dependent. The
  dependency was merely *slow*; the bug is that the dependent was allowed to start too early.
- Forgetting that races can corrupt durable state. After a race-crash, check what the loser
  half-created (topics, tables, buckets) — volumes preserve mistakes too.

### Where it is used

- Every microservice stack has this exact shape: app → database, app → broker, consumer →
  schema registry. The signature transfers unchanged.
- CI is where won races go to die: cold caches and shared runners stretch every gap.
- Kubernetes has the same concept with different spelling: readiness probes and init
  containers instead of healthchecks and `depends_on`.

### Link to the task

This is Step 6 verbatim: "Break it on purpose: delete the `condition:` line for
schema-registry, `docker compose down && up`, watch Schema Registry crash-loop... Restore the
condition. This failure mode — service starts before its dependency is ready — is one of the
most common infra bugs; cause it deliberately once and you'll recognise it instantly." The
twist this machine added: it was fast enough to *win* the broken race three times, which
taught the sharper lesson that green runs don't prove safety — scars and the earlier organic
crash do.

### Do it yourself

1. In `deploy/compose/docker-compose.infra.yml`, replace the registry's `depends_on` block
   with the bare form:

   ```yaml
       depends_on:
         - kafka
   ```

2. Run the ceremony three times, recording evidence after each:

   ```
   docker compose -f deploy/compose/docker-compose.infra.yml down -v
   docker compose -f deploy/compose/docker-compose.infra.yml up -d
   sleep 20
   docker compose -f deploy/compose/docker-compose.infra.yml ps -a | grep schema
   docker inspect -f '{{.RestartCount}}' infra-schema-registry-1
   docker compose -f deploy/compose/docker-compose.infra.yml logs schema-registry | grep -c 'Node -1 disconnected'
   ```

   A slower laptop, possibly pulling cold images, has a decent chance of the **full crash**:
   `Exited (1)` or `Restarting`, spam in the hundreds. That is the exhibit — study it. If the
   runs come up green instead, the nonzero spam count is your proof you raced and won.

   *If you hit the poisoned-topic variant* — registry crash-looping with
   `Expected cleanup.policy to be 'compact' but it is delete` even after Kafka shows healthy —
   delete the damaged, empty topic and let a cleanly-started registry recreate it:

   ```
   docker exec infra-kafka-1 /opt/kafka/bin/kafka-topics.sh \
     --bootstrap-server localhost:9092 --delete --topic _schemas
   docker compose -f deploy/compose/docker-compose.infra.yml up -d
   ```

3. Restore the condition exactly as it was:

   ```yaml
       depends_on:
         kafka:
           condition: service_healthy
   ```

4. One more ceremony, expecting **spam: 0**:

   ```
   docker compose -f deploy/compose/docker-compose.infra.yml down -v
   docker compose -f deploy/compose/docker-compose.infra.yml up -d --wait
   docker compose -f deploy/compose/docker-compose.infra.yml logs schema-registry | grep -c 'Node -1 disconnected'
   ```

**Done when:** you have seen the registry either crash or carry scars without the condition,
the spam count is 0 with it restored, and you can list the failure signature from memory.

### Summary for revision

- Bare `depends_on` orders creation only; the dependent starts ~5–10s before Kafka can answer.
- The dependent retries inside a patience budget: ready-in-time → green **with scars**;
  too slow → `Exited (1)`, possibly corrupted durable state (`_schemas` with wrong policy).
- Milliseconds decide which — that's a race; intermittent results are what "flaky" means.
- Three green runs prove nothing. You can't test a race away; remove it with a readiness gate.
- Signature: dependent `Restarting`/`Exited (1)` while dependency is `Up`; connection spam in
  the dependent's logs; `RestartCount > 0`; sometimes damage preserved in volumes.
- With `condition: service_healthy` restored: spam 0 — never raced, deterministic.
- `sleep` in scripts is not a fix; it just bets on winning by a bigger margin.

### Check questions, with answers

**Question 1.** All three no-condition runs came up green. A teammate concludes the condition
is unnecessary. Give the two pieces of evidence from this task that prove them wrong.

**Answer.** First: every green run carries **scars** — exactly 5 `Node -1 disconnected` lines
per run, proving the registry raced an unready Kafka every time and merely won. Second: the
**organic crashes earlier in this same task** — the identical raced start-up lost twice on
this same machine during the volume work (`Exited (1)`, hundreds of disconnect lines, and a
poisoned `_schemas` topic). Same configuration, opposite outcomes: the condition isn't there
for the runs that pass, it's there for the run that won't.

**Question 2.** Why does the same compose file race-crash on one machine and sail through on
another? Name the concrete factors.

**Answer.** The crash depends on whether Kafka's boot time exceeds the registry's fixed
patience budget, and Kafka's boot time moves with: image warmth (already pulled vs cold
download), whether the volume is fresh (first-boot formatting is slower), CPU and disk load
at that moment (everything recreating at once), and overall machine speed. A fast idle Mac
keeps the gap at ~5 seconds and wins; a loaded CI runner or older laptop stretches it past
the budget and loses. The budget is constant; the gap is not — that's why it's flaky.

**Question 3.** In the wild: service X is `Restarting`; its dependency shows `Up (healthy)`.
What are the first three things you check, in order?

**Answer.** 1) **X's logs** (`docker compose logs X`): look for repeated connection failures
to the dependency — that pattern says X started before the dependency was ready, not that the
dependency is broken now. 2) **X's restart count**
(`docker inspect -f '{{.RestartCount}}' <container>`): nonzero confirms crash-and-revive
cycles rather than a one-off. 3) **The compose file's `depends_on`**: does X use bare form or
`condition: service_healthy`, and does the dependency even have a healthcheck to gate on? If
the gate is missing, that's the root cause — add the healthcheck and the condition rather
than restarting X until it wins its race.

### Your doubts

None in this lesson.

---

## Lesson 13 — Bind mounts: config you edit, delivered into the container (Step 7)

### Definition

A **bind mount** takes an exact file or folder from the host and makes it appear at a chosen
path inside a container — not a copy, the **same file**, visible from both sides at once. In
the `-v` flag the difference from a named volume is the left side: a *name* (`pg-data:...`)
means named volume; a *path* (`$PWD/...`, must be absolute) means bind mount. A trailing
`:ro` makes it read-only from inside the container.

### Why it is needed

Named volumes are perfect for *state* — bytes the program writes, that you never touch by
hand. Config is the opposite: a file **you** edit, in **your** editor, that the container
must see instantly. In a named volume you couldn't comfortably open it in the IDE; baked
into the image, every tweak would cost a rebuild-and-recreate. A bind mount gives the
sub-second loop: edit on host → reload signal → live.

### How it works, step by step

1. `-v "$PWD/deploy/compose/nginx/nginx.conf:/etc/nginx/nginx.conf:ro"` — host path :
   container path : flags. The kernel maps the container path onto the host file, so a save
   on the host is instantly the content at the container path.
2. But programs read config **once, at startup**, into memory. The mount changes the bytes
   on disk; it does not change the copy in the program's memory. These are two separate
   layers, and the edit only reaches the first.
3. So after editing, you send the program a reload signal — for nginx,
   `docker exec <container> nginx -s reload` — and it re-reads the file. No rebuild, no
   container recreation, under a second.
4. `:ro` means the container can read but never write the file. The host owns the truth.

### Example — real commands, real output from this machine

A throwaway nginx whose whole config returns one fixed sentence:

```
docker run -d --name bindmount-demo --rm -p 8088:80 \
  -v "$PWD/deploy/compose/nginx/nginx.conf:/etc/nginx/nginx.conf:ro" \
  nginx:alpine

curl -s http://localhost:8088/
hello from nginx - version 1
```

Then the file was edited **on the host** (changed to "version 2, edited on the host") with
no Docker command at all:

```
--- after host edit, BEFORE reload ---
hello from nginx - version 1
--- the container already sees the new bytes: ---
      return 200 "hello from nginx - version 2, edited on the host\n";
--- nginx -s reload ---
2026/11/19 18:21:41 [notice] 37#37: signal process started
hello from nginx - version 2, edited on the host
--- proof the :ro flag bites ---
sh: can't create /etc/nginx/nginx.conf: Read-only file system
```

The two layers, caught in the act: the file inside the container changed the instant the
host save happened (`grep` shows version 2), yet curl still served version 1 until the
reload — because nginx's *memory* still held the old config. After `nginx -s reload`: live.
And the container's attempt to modify its own config died on the read-only mount.
`docker stop bindmount-demo` then removed everything (`--rm`).

### Important terms

| Term | Meaning |
|---|---|
| Bind mount | Host path mapped into the container — same file, two viewpoints |
| Named volume | Docker-managed storage, referenced by name, for program-written state |
| `:ro` | Read-only flag: container may read, never write |
| Reload signal | Telling a running program to re-read config (e.g. `nginx -s reload`) |
| `--rm` | Delete the container automatically when it stops |

### Common mistakes

- Editing a bind-mounted config and expecting instant behavior change. The mount delivers
  bytes to disk; the program's memory needs a reload (or restart) to notice.
- Mistyping the host path in `-v`. Docker then creates an empty **directory** at that name,
  and nginx crashes with `"/etc/nginx/nginx.conf" is a directory` in `docker logs`.
- Using a relative host path. Bind-mount paths must be absolute — hence `$PWD/...`.
- Skipping `:ro` on config. A buggy or compromised container process could then rewrite a
  file that lives in your repo.
- Using bind mounts for database state. Host filesystem semantics (especially on macOS,
  where Docker runs in a VM) are slower and subtly different; state belongs in named volumes.

### Where it is used

- Phase 10 puts a real nginx gateway in front of our services and Phase 11 edits its routing
  constantly — each tweak will cost one `nginx -s reload` instead of a rebuild.
- Every stack mounts config this way: Prometheus's `prometheus.yml`, Grafana dashboards,
  app `application.yml` overrides during development.
- The same idea powers live-reload dev setups: source code bind-mounted into a container,
  file-watcher inside restarts the process on save.

### Link to the task

Step 7 verbatim: create `deploy/compose/nginx/nginx.conf`, run a throwaway nginx with the
conf bind-mounted, edit on the host, `nginx -s reload` inside, see the change without
rebuilding — then state the rule in `docs/local-setup.md`. The rule now lives there:
**named volumes for state** (program-written bytes: `pg-data`, `redis-data`, `kafka-data`,
`minio-data`, `mailpit-data`); **bind mounts for config you edit** (editor files, `:ro`,
picked up with at most a reload signal). Memory hook: **who writes the file?** The program →
named volume. You → bind mount.

### Do it yourself

1. Create `deploy/compose/nginx/nginx.conf` in the repo:

   ```nginx
   events {}

   http {
     server {
       listen 80;

       location / {
         return 200 "hello from nginx - version 1\n";
       }
     }
   }
   ```

2. From the repo root (so `$PWD` expands right), run and check:

   ```
   docker run -d --name bindmount-demo --rm -p 8088:80 \
     -v "$PWD/deploy/compose/nginx/nginx.conf:/etc/nginx/nginx.conf:ro" \
     nginx:alpine
   curl -s http://localhost:8088/
   ```

   Expect: `hello from nginx - version 1` (first run pulls the image for a few seconds).
   Failure you might see: a mistyped `-v` host path makes Docker create a *directory* there;
   nginx dies on boot and `docker logs bindmount-demo` shows
   `"/etc/nginx/nginx.conf" is a directory`. Fix the path, delete the stray empty dir.

3. Edit the file in your editor (version 1 → version 2), then watch the two layers:

   ```
   curl -s http://localhost:8088/                                  # still version 1
   docker exec bindmount-demo grep return /etc/nginx/nginx.conf    # file inside: version 2
   docker exec bindmount-demo nginx -s reload
   curl -s http://localhost:8088/                                  # now version 2
   ```

4. Prove `:ro`:

   ```
   docker exec bindmount-demo sh -c 'echo hacked >> /etc/nginx/nginx.conf'
   ```

   Expect: `Read-only file system`.

5. Clean up, and write the rule into `docs/local-setup.md` (named volumes for state, bind
   mounts for config you edit):

   ```
   docker stop bindmount-demo
   docker ps -a | grep bindmount    # expect: nothing - --rm removed it
   ```

**Done when:** curl flipped from version 1 to version 2 *only after* the reload, the `:ro`
write failed, and the rule is in `docs/local-setup.md` in your own words.

### Summary for revision

- Bind mount = host path mapped into the container; same file, both sides, instantly.
- Left side of `-v` decides: name → named volume; absolute path → bind mount.
- Two layers: disk (mount updates instantly) vs program memory (needs a reload signal).
- nginx reload: `docker exec <container> nginx -s reload` — sub-second, no rebuild.
- `:ro` = container reads, never writes; the host owns the truth.
- The rule (in `docs/local-setup.md`): named volumes for state, bind mounts for config you
  edit. Who writes the file? Program → volume. You → bind mount.

### Check questions, with answers

**Question 1.** You edit a bind-mounted config and curl still shows the old behavior. Name
the two layers involved, and which one your edit reached.

**Answer.** Layer one: the **file on disk** — the bind mount updated it inside the container
the instant you saved (provable with `docker exec <c> cat/grep` on the file). Layer two: the
**program's in-memory copy** of the config, read once at startup — your edit never touched
it. The fix is a reload signal (`nginx -s reload`) or a container restart so the program
re-reads the file.

**Question 2.** For each, named volume or bind mount — Postgres's data directory; the nginx
routing config; Kafka's log segments; an application's `application.yml` you tune during
development?

**Answer.** Postgres data directory: **named volume** (program-written state). nginx routing
config: **bind mount** (you edit it, container reloads it). Kafka log segments: **named
volume** (broker-written state). `application.yml` tuned during development: **bind mount**
(editor file, instant visibility). The rule: who writes the file — program → volume, you →
bind mount.

**Question 3.** Why mount config `:ro`? Give one concrete bad thing the flag prevents.

**Answer.** Because the file lives in your repo on the host, and the container has no
business writing it. Concretely: a buggy entrypoint or a compromised process inside the
container could rewrite the config — polluting your git working tree, persisting a malicious
change that survives container recreation (it's on the host!), or silently diverging from
what you think you're running. With `:ro`, any such write dies with
`Read-only file system`, exactly as the demo showed.

### Your doubts

None in this lesson.

---

## Lesson 14 — `.env`: credentials out of the YAML — convenience, not secrecy (Step 8)

### Definition

`.env` is a plain `KEY=VALUE` file that Docker Compose reads **automatically** from the
project directory (the folder the compose file lives in). Every `${VARIABLE}` placeholder in
the YAML is replaced with the value from `.env` when Compose resolves the file — before
Docker sees anything. Credentials move out of the shared, committed YAML into a local,
git-ignored file.

### Why it is needed

Credentials and code have different lifecycles. The compose file's destiny is to be committed
and shared; passwords written literally inside it travel with every clone, and anyone wanting
a different local value must edit a shared file (and will eventually commit it by accident).
`.env` gives each machine its own values, keeps them out of git, and leaves one single place
to change them.

### How it works, step by step

1. Create `deploy/compose/.env` with `KEY=VALUE` lines — no quotes, no `export`.
2. In the YAML, write `${KEY}` wherever the value appeared. This works **anywhere** in the
   file — environment values, healthcheck test strings, shell commands in `command:` —
   because the substitution is textual, performed by Compose at config-resolution time. The
   container never sees `${...}`. (Need a literal `$` for the container's shell? Write `$$`.)
3. Add `.env` to `.gitignore` so it never reaches the repository.
4. Prove it with `docker compose config`: prints the fully **resolved** file — YAML parsed,
   `.env` applied, placeholders replaced. If a variable is missing, Compose warns
   `variable is not set. Defaulting to a blank string` and limps on with empty values.
5. Compose compares *resolved* config with what's running, so if the values are unchanged,
   `up -d` recreates nothing — the refactor is invisible to the running stack.

### Example — real commands, real output from this machine

The file (`deploy/compose/.env`):

```
POSTGRES_PASSWORD=dev
MINIO_ROOT_USER=minio
MINIO_ROOT_PASSWORD=minio12345
```

Five YAML spots became placeholders — three `environment:` values plus the two sneaky ones
*inside command strings* (MinIO's healthcheck, minio-init's `mc alias set` line). Proof of
resolution:

```
$ docker compose -f deploy/compose/docker-compose.infra.yml config | grep -E 'PASSWORD|ROOT_USER|until'
      MINIO_ROOT_PASSWORD: minio12345
      MINIO_ROOT_USER: minio
      POSTGRES_PASSWORD: dev
        until mc alias set local http://minio:9000 minio minio12345; do
```

With `.env` temporarily moved away:

```
level=warning msg="The \"MINIO_ROOT_PASSWORD\" variable is not set. Defaulting to a blank string."
level=warning msg="The \"MINIO_ROOT_USER\" variable is not set. Defaulting to a blank string."
level=warning msg="The \"POSTGRES_PASSWORD\" variable is not set. Defaulting to a blank string."
      MINIO_ROOT_USER: ""
      POSTGRES_PASSWORD: ""
```

And `up -d` after the whole refactor:

```
 Container infra-postgres-1 Running
 Container infra-minio-1 Running
```

Nothing recreated — same resolved values, new plumbing. `psql` with the `.env` password
still answers.

Bonus incident: while editing the minio-init command block, the YAML indentation broke (the
`until` line ended up deeper than the lines after it, which cuts a block scalar short).
`docker compose config` caught it instantly:

```
yaml: line 126: could not find expected ':'
```

New habit: **run `docker compose config` after every YAML edit** — a free pre-flight check
for broken YAML and missing variables, before any container is touched.

### Important terms

| Term | Meaning |
|---|---|
| `.env` | KEY=VALUE file Compose auto-loads from the project directory |
| Project directory | The folder containing the compose file; where `.env` must live |
| `${VAR}` | Placeholder replaced by Compose at config-resolution time |
| `$$` | Escape for a literal `$` the container's shell should receive |
| `docker compose config` | Prints the resolved file: YAML parsed, `.env` applied |
| Resolved config | What Compose compares against running containers to decide recreation |

### Common mistakes

- Expecting an error when `.env` is missing. Compose only **warns** and substitutes empty
  strings; the failure then surfaces downstream in confusing ways (Postgres refuses to
  initialize, MinIO rejects blank credentials). On a misbehaving fresh clone, check for the
  missing `.env` first.
- Forgetting placeholders hidden inside command strings — grep the YAML for the literal
  values after the refactor to catch stragglers.
- Putting `.env` somewhere else. Compose reads it from the compose file's folder; a `.env`
  at the repo root is ignored (unless you pass `--env-file`).
- Believing the values are now secret (see the honesty clause below).
- Editing YAML without running `docker compose config` afterwards.

### Where it is used

- Every real compose project does exactly this; CI systems inject the same variables from
  their own secret stores instead of a file.
- The same `${VAR}` syntax reappears in later phases wherever config is templated.
- Spring's `application.yml` uses the identical idea (`${DB_PASSWORD}`) resolved from the
  process environment.

### Link to the task

Step 8 verbatim: move credentials out of YAML into `.env` beside the compose file, reference
as `${POSTGRES_PASSWORD}` etc., add `.env` to `.gitignore`, prove with
`docker compose config`, and be honest: **convenience, not secrecy**. The honesty in full:
`.env` is plaintext on disk; `docker compose config` prints the values; `docker inspect`
shows a container's environment. What it actually buys: values stay out of git, each machine
keeps its own, one place to change them. Real secret management (vaults, runtime injection)
is a later phase. The repo root now has a `.gitignore` with `deploy/compose/.env` — written
before this folder is even a git repository, so the protection exists from day one.

### Do it yourself

1. Create `deploy/compose/.env`:

   ```
   POSTGRES_PASSWORD=dev
   MINIO_ROOT_USER=minio
   MINIO_ROOT_PASSWORD=minio12345
   ```

2. In `deploy/compose/docker-compose.infra.yml`, replace every literal credential with
   placeholders — **five spots**: postgres `POSTGRES_PASSWORD`; minio `MINIO_ROOT_USER` and
   `MINIO_ROOT_PASSWORD`; `minio minio12345` inside the minio healthcheck test string; the
   same pair inside minio-init's `until mc alias set` command.

3. Create `.gitignore` at the repo root containing:

   ```
   deploy/compose/.env
   ```

4. Prove resolution:

   ```
   docker compose -f deploy/compose/docker-compose.infra.yml config | grep -E 'POSTGRES_PASSWORD|MINIO_ROOT'
   ```

   Expect real values (`dev`, `minio`, `minio12345`), no `${...}` left. Failures to see once
   on purpose: mangled indentation → `yaml: line N: could not find expected ':'`; `.env`
   renamed away → `variable is not set. Defaulting to a blank string` warnings.

5. Confirm the running stack doesn't care:

   ```
   docker compose -f deploy/compose/docker-compose.infra.yml up -d
   ```

   Expect every container `Running` — nothing recreated.

**Done when:** `config` shows real values with no placeholders, you've seen the missing-`.env`
warning once on purpose, and `up -d` recreates nothing.

### Summary for revision

- `.env` = KEY=VALUE, auto-loaded from the compose file's folder; referenced as `${VAR}`.
- Substitution is textual, by Compose, at resolve time — works anywhere, even inside
  healthcheck and command strings; container never sees `${...}`; `$$` = literal `$`.
- Missing variable → warning + blank string, not an error; breakage surfaces downstream.
- `docker compose config` = resolved file; run it after every YAML edit.
- Same resolved values → `up -d` recreates nothing.
- `.gitignore` the `.env`. Convenience, not secrecy: plaintext on disk, visible in
  `config` and `docker inspect`; it only keeps values out of git and per-machine.

### Check questions, with answers

**Question 1.** A teammate clones the repo, runs `up -d`, and Postgres crash-loops with
"superuser password is not specified" while your machine works fine. What happened, and
what's the 10-second diagnostic command?

**Answer.** Their clone has no `.env` — it's git-ignored, so it never traveled with the
repo. Compose warned `POSTGRES_PASSWORD variable is not set. Defaulting to a blank string`
and started Postgres with an empty password, which its image refuses. Diagnostic:
`docker compose -f deploy/compose/docker-compose.infra.yml config | grep POSTGRES_PASSWORD`
— shows `""` instead of a value (the warnings on stderr say the same). Fix: create
`deploy/compose/.env` with the three values.

**Question 2.** `${POSTGRES_PASSWORD}` appears in the YAML. Name the exact moment it gets
replaced, and one consequence of that.

**Answer.** It is replaced by **Compose itself, when it resolves the file** — before the
Docker daemon or any container is involved. Consequence: the substitution works anywhere in
the YAML, including inside the MinIO healthcheck string and minio-init's shell command,
because it's a textual replacement on the config, not a runtime environment lookup. (Also
why `docker compose config` shows final values, and why a literal `$` for the container's
shell must be written `$$`.)

**Question 3.** `.env` is in `.gitignore` — so are the passwords now *secret*? Name two ways
someone on this machine still reads them, and what `.env` actually protects against.

**Answer.** Not secret. On this machine: `cat deploy/compose/.env` (plaintext on disk), or
`docker compose config` (prints resolved values), or `docker inspect infra-postgres-1`
(shows the container's environment). What `.env` protects against: committing credentials
into git history and forcing every machine to share the same values. That's all — real
secrecy needs a secret manager, which arrives in a later phase.

### Your doubts

None in this lesson.

---

## Lesson 15 — Profiles: start only the slice of the stack you need (Step 9)

### Definition

A **profile** is a tag on a service (`profiles: ["core", "full"]`). A tagged service starts
only when one of its profiles is activated — by `--profile <name>` on the command line or the
`COMPOSE_PROFILES` variable. A service **without** tags starts always. Profiles give named
slices of one compose file: no copies of the YAML, no commenting out.

### Why it is needed

The stack has seven entries and Kafka alone eats ~600 MiB, but a day of pure database work
needs exactly two containers. On an 8 GiB VM, starting everything every time wastes memory
and boot seconds. `--profile core` starts just Postgres and Redis; `--profile full` brings
the world.

### How it works, step by step

1. Tag every service. The rule's sharp half: untagged services run *unconditionally* —
   profiles can only exclude, never include an untagged service. One untagged service would
   gate-crash every profile, so for `--profile core` to mean *exactly two*, all seven entries
   carry tags.
2. The groups: postgres+redis → `core,full`; kafka+schema-registry → `events,full`;
   minio+minio-init → `media,full`; mailpit → `full` only (the task fixes core at exactly
   two). Every service also carries `full`, so `--profile full` is the whole stack.
3. `observe` and `edge` are planned but literally don't exist yet — a profile only comes
   into existence when some service references it. Prometheus/Grafana will create `observe`,
   the nginx gateway creates `edge`, in later phases.
4. The trap: once everything is tagged, bare `up -d` starts **nothing** (no active profile =
   no selected services, and `down` gets equally confused). Fix: `COMPOSE_PROFILES=full` in
   `.env` — Compose honors it from the same file it reads for `${...}` values.
5. Precedence (tested live): an explicit `--profile` on the command line **overrides** the
   `.env` default. Default in the file, override on the CLI.
6. Explicit beats tags everywhere: `up -d mailpit` runs mailpit even when its profile isn't
   active.

### Example — real commands, real output from this machine

Dry-run selections, no containers touched:

```
$ docker compose -f deploy/compose/docker-compose.infra.yml config --profiles
core
events
full
media            ← observe/edge absent: nothing references them yet

$ docker compose -f ... --profile core config --services
postgres
redis            ← exactly two, as the task demands

$ COMPOSE_PROFILES=full docker compose -f ... --profile core config --services
postgres
redis            ← CLI flag beat the variable
```

The real thing:

```
$ docker compose -f ... down && docker compose -f ... --profile core up -d --wait
infra-postgres-1 Up 5 seconds (healthy)
infra-redis-1    Up 5 seconds (healthy)

$ docker compose -f ... --profile full up -d --wait && docker compose -f ... ps -a
infra-kafka-1            Up 7 seconds (healthy)
infra-mailpit-1          Up 7 seconds (healthy)
infra-minio-1            Up 7 seconds (healthy)
infra-minio-init-1       Exited (0) 1 second ago
infra-postgres-1         Up 13 seconds (healthy)   ← 13s, not 7s:
infra-redis-1            Up 13 seconds (healthy)   ← widening didn't recreate them
infra-schema-registry-1  Up (health: starting)
```

Converge, as always: `--profile full` only added what was missing.

### Important terms

| Term | Meaning |
|---|---|
| `profiles:` | Tag list on a service; service runs only when a tag is activated |
| `--profile X` | CLI activation; repeatable; overrides `COMPOSE_PROFILES` |
| `COMPOSE_PROFILES` | Default activation, honored from `.env`; comma-separated |
| Untagged service | Runs always — profiles exclude, they never include |
| `config --profiles` | Lists profiles that exist (= are referenced by some service) |
| `config --services` | Dry-run: which services the current activation selects |

### Common mistakes

- Leaving one service untagged and wondering why it starts with every profile. Tag
  everything, or accept the gate-crasher.
- Tagging everything and forgetting `COMPOSE_PROFILES=full` in `.env` — bare `up`/`down`
  then does nothing, silently.
- Splitting dependents across profiles: if schema-registry were in a profile without kafka,
  starting it alone would drag kafka in anyway (`depends_on` wins) — keep dependent pairs in
  the same group, as minio+minio-init are.
- Expecting `observe`/`edge` in `config --profiles` before any service references them.

### Where it is used

- Daily workflow: `--profile core` for database work, `events` when Kafka matters, `full`
  before integration tests. The future `make up profile=core` passes the flag through.
- CI starts only the slice a test suite needs — faster pipelines, less flake.
- The same idea appears in Spring as profiles (`application-dev.yml`) and in k8s as
  overlays: one definition, many named activations.

### Link to the task

Step 9 verbatim: profiles core (postgres+redis), events (kafka+schema-registry), media
(minio+minio-init), observe/edge (empty for now), full; test that `--profile core` starts
exactly two. Proven above — plus two discoveries the task didn't spell out: empty profiles
literally don't exist until referenced, and `COMPOSE_PROFILES=full` in `.env` is needed so
bare commands keep meaning "everything" once all services are tagged.

### Do it yourself

1. In `deploy/compose/docker-compose.infra.yml`, add one `profiles:` line right under each
   service name: postgres/redis `["core", "full"]`, kafka/schema-registry
   `["events", "full"]`, minio/minio-init `["media", "full"]`, mailpit `["full"]`.

2. Add to `deploy/compose/.env`:

   ```
   COMPOSE_PROFILES=full
   ```

3. Dry-run the selections:

   ```
   docker compose -f deploy/compose/docker-compose.infra.yml config --profiles
   docker compose -f deploy/compose/docker-compose.infra.yml --profile core config --services
   docker compose -f deploy/compose/docker-compose.infra.yml config --services
   ```

   Expect: `core events full media`; then exactly `postgres redis`; then all seven. See the
   trap once: comment out `COMPOSE_PROFILES`, rerun the last command, get empty output.
   Uncomment.

4. The real thing:

   ```
   docker compose -f deploy/compose/docker-compose.infra.yml down
   docker compose -f deploy/compose/docker-compose.infra.yml --profile core up -d --wait
   docker compose -f deploy/compose/docker-compose.infra.yml ps
   ```

   Expect exactly two, both `(healthy)`. Then:

   ```
   docker compose -f deploy/compose/docker-compose.infra.yml --profile full up -d --wait
   docker compose -f deploy/compose/docker-compose.infra.yml ps -a
   ```

   Expect all seven, postgres/redis showing older uptimes than the newcomers.

**Done when:** `--profile core` starts exactly two healthy containers, `--profile full` adds
the rest without recreating them, and bare `config --services` lists everything.

### Summary for revision

- `profiles: ["core", "full"]` on a service; activate with `--profile X` or
  `COMPOSE_PROFILES`.
- Untagged services always run — so tag all of them if slices must be exact.
- All tagged + no activation = bare `up` does nothing → set `COMPOSE_PROFILES=full` in
  `.env`.
- CLI `--profile` overrides the `.env` default; naming a service explicitly overrides tags.
- A profile exists only once referenced (`observe`/`edge` arrive with later services).
- Groups here: core=postgres+redis, events=kafka+registry, media=minio+init, mailpit
  full-only, everything also in full.
- Widening a profile converges — running services are not recreated.

### Check questions, with answers

**Question 1.** A teammate adds a new `pgadmin` service with no `profiles:` line. What
happens to `--profile core`, and why is that the rule rather than a bug?

**Answer.** `--profile core` now starts three containers — postgres, redis, *and pgadmin* —
because an untagged service runs unconditionally; profiles can only exclude tagged services,
never rope in untagged ones. It's the rule because it keeps profile-free compose files
working exactly as before (profiles are opt-in) and lets must-always-run services (a proxy,
say) simply omit the key. The fix is to tag `pgadmin` into an appropriate group.

**Question 2.** Why don't `observe` and `edge` show up in `config --profiles`, even though
the task names them?

**Answer.** A profile has no declaration of its own anywhere — it exists purely by being
mentioned in some service's `profiles:` list. No service references `observe` or `edge` yet,
so Compose has never heard of them. They'll spring into existence the moment
Prometheus/Grafana (observe) and the nginx gateway (edge) are added in later phases.

**Question 3.** With `COMPOSE_PROFILES=full` in `.env`, what do these each select: bare
`up -d`; `--profile core up -d`; `up -d mailpit`?

**Answer.** Bare `up -d`: the `.env` default activates `full`, so the entire stack. 
`--profile core up -d`: the CLI flag overrides the `.env` default — exactly postgres and
redis. `up -d mailpit`: naming a service explicitly runs it regardless of profile
activation — mailpit starts (plus any `depends_on`, of which it has none).

### Your doubts

None in this lesson.

---

## Lesson 16 — The Makefile: one word per intention (Step 10)

### Definition

**make** is a build tool from 1976 that modern teams use as a **command menu**: a `Makefile`
at the repo root maps one-word names (`make up`, `make psql`, `make reset`) onto the long
commands they stand for. A target's recipe runs when you name the target. The daily
incantation `docker compose -f deploy/compose/docker-compose.infra.yml --profile core up -d
--wait` becomes `make up profile=core`.

### Why it is needed

Seventy-character commands typed ten times a day get mistyped, and the flags that matter
(`--wait`? `-v`?) live only in people's memories. A Makefile makes the right way the easy
way: one word per intention, defaults baked in, and the menu itself (`make help`) documents
what the repo can do.

### How it works, step by step

1. A `Makefile` entry is `target: ## description` followed by recipe lines. **Recipe lines
   must start with a TAB** — spaces give the notorious `*** missing separator.  Stop.` error
   (a 1970s design accident kept for compatibility).
2. Make's native job is building *files*: if a file named like the target exists and is
   fresh, make skips the recipe. Our targets aren't files, so they're declared `.PHONY` —
   otherwise a stray file named `up` would permanently disable `make up` with
   `make: 'up' is up to date.`
3. Variables: `COMPOSE := docker compose -f deploy/compose/docker-compose.infra.yml` defines
   the long prefix once. `profile ?= full` means "default to full **unless** a value was
   given" — and `make up profile=core` gives one on the command line, which overrides the
   file default. That single line is the whole passthrough feature.
4. Sugar: `@` hides the command echo; `$$` passes a literal `$` to the shell;
   `.DEFAULT_GOAL := help` makes bare `make` print the menu; the `help` target greps the
   `##` comments, so the menu writes itself.
5. `make up` does two things: `up -d --wait` for the chosen profile, then — for media/full —
   explicitly runs the bucket job: `docker compose run --rm minio-init` (see the war story).

### Example — real commands, real output from this machine

The tab rule broken on purpose (recipe indented with four spaces):

```
/tmp/broken.mk:2: *** missing separator.  Stop.
```

Bare `make` — the menu:

```
  make help       this menu
  make up         start the stack and wait for healthy (make up profile=core|events|media|full)
  make down       stop and remove containers; data survives in the volumes
  make reset      DESTRUCTIVE factory reset: containers AND volumes gone
  make logs       follow logs (make logs s=kafka for one service)
  make ps         status of every service
  make psql       interactive SQL shell in Postgres
  make redis-cli  interactive Redis shell
  make topics     list Kafka topics
  make test       run the Java tests
  make fmt        format the code
  make smoke      end-to-end smoke test against the running stack
  make seed       load demo data
```

The ceremony, timed, from wiped volumes:

```
$ make reset && time make up
...
 Container infra-schema-registry-1 Healthy
Added `local` successfully.
Bucket created successfully `local/media`.
bucket 'media' ready
make up  ... 17.973 total
```

**18 seconds** from nothing to six healthy services plus the bucket — the task's 90-second
budget has 5× headroom. Repeat `make up`: exit 0 (converge; the job re-runs harmlessly
thanks to `mc mb --ignore-existing`). `make up profile=core`: exit 0, two containers.

### The war story: `--wait` vs the job container

The first `make up` returned `make: *** [up] Error 1` while every container came up healthy.
Diagnosis: **Compose 5.8.0's `--wait` counts a finished one-shot container as a failure** —
it printed `container infra-minio-init-1 exited (0)` and exited 1, even though exit 0 *is*
success for a job. Worse, the `| tail -1` pipes used all evening had been masking that exit
code (a pipeline reports the last command's status — `tail`'s, always 0). Two free lessons:
your tools have versions and bugs too, and check `$?`, not just the output.

The fix, with no error-masking: minio-init moved to its own profile (`init`), out of
`full`'s wait path, and `make up` runs it explicitly after the wait:

```makefile
up:
	$(COMPOSE) --profile $(profile) up -d --wait
	@case "$(profile)" in media|full) $(COMPOSE) run --rm minio-init ;; esac
```

`compose run --rm` runs a one-off container of that service (explicitly named services run
even when their profile is inactive), shows its output, propagates its **real** exit code,
and deletes the container. A failing bucket job still fails `make up` — the compose bug is
dodged without hiding genuine errors behind a `|| true`.

### Important terms

| Term | Meaning |
|---|---|
| Target | The name after `make` (`up`, `psql`); its recipe lines run |
| Recipe | The shell commands under a target — each line MUST start with a TAB |
| `.PHONY` | Declares targets that aren't files, so stray files can't disable them |
| `?=` | Assign only if not already set — defaults the CLI can override |
| `@` prefix | Don't echo the command itself |
| `$$` | A literal `$` for the shell (make eats single ones) |
| `.DEFAULT_GOAL` | What bare `make` runs (here: `help`) |
| `compose run --rm` | One-off container of a service; real exit code; auto-removed |

### Common mistakes

- Spaces instead of a tab → `*** missing separator.  Stop.` Decode it once: *recipe line
  doesn't start with TAB*.
- Forgetting `.PHONY`, then someone creates a file named like a target and the target
  silently stops running.
- Masking a flaky command with `|| true` — it also masks every real failure. Restructure
  instead (as with the bucket job).
- Trusting `cmd | tail -1` pipelines in scripts: the pipeline's exit code is the last
  command's, not `cmd`'s.
- Editors that convert tabs to spaces — disable that for Makefiles.

### Where it is used

- `make up && make test` becomes the universal entry point: new teammates, CI (Task 0.4),
  and future-you all use the same menu.
- Nearly every serious repo has this file; knowing the tab rule, `.PHONY`, and `?=` covers
  90% of real-world Makefiles.
- The done-when of this whole task is phrased in make: `make reset && make up` healthy in
  under 90s, `make psql` opens a shell.

### Link to the task

Step 10 verbatim: Makefile with up/down/reset/logs/psql/redis-cli/topics/test/fmt plus
smoke/seed placeholders, and `make up profile=core` passthrough. All present; `up` uses
`--wait` so the done-when's "healthy" is baked into the command, and the placeholders print
polite one-liners until their tasks arrive (next lesson maps which task makes each real).

### Do it yourself

1. Check make exists: `make --version` (GNU Make 3.81 is fine; missing →
   `xcode-select --install`).

2. Create `Makefile` at the repo root with the lesson's content — every recipe line starts
   with a **tab**. In the compose file, change minio-init to `profiles: ["init"]` with the
   war-story comment.

3. See the tab error once: indent any recipe with spaces, run it, read
   `*** missing separator.  Stop.`, undo.

4. The full rehearsal:

   ```
   make                   # menu (no target = help)
   make reset             # everything gone, volumes included
   time make up           # all Healthy + "bucket 'media' ready", well under 90s
   make ps                # six services, (healthy) everywhere
   make topics            # __consumer_offsets, _schemas
   make psql              # SQL shell: select 1; then \q
   make redis-cli         # PING -> PONG, then quit
   make test              # placeholder line
   make up profile=core   # exit 0
   ```

   Failure to know: `Error 1` from `make up` while everything looks healthy = the wait path
   includes a container that is *supposed* to exit; check `make ps` for an `Exited (0)` job
   and keep jobs out of `--wait`'s scope.

**Done when:** bare `make` prints the menu, `make reset && make up` reaches all-healthy plus
bucket in under 90 seconds, `make psql` opens a SQL shell, and you can explain the tab rule.

### Summary for revision

- Makefile = command menu; target → TAB-indented recipe; `make <target>` runs it.
- TAB rule: spaces → `missing separator`. `.PHONY` protects non-file targets.
- `COMPOSE := ...` defines the long prefix once; `profile ?= full` + CLI override =
  `make up profile=core`.
- `@` silences echo; `$$` escapes `$`; `.DEFAULT_GOAL := help`; help greps `##` comments.
- Compose 5.8 `--wait` fails on exited jobs (even exit 0) → job in own `init` profile,
  `make up` runs it explicitly via `compose run --rm`. No `|| true`.
- Pipelines report the last command's exit code — don't let `tail` eat failures.
- Measured: `make reset && make up` = 18s to all-healthy + bucket (budget: 90s).

### Check questions, with answers

**Question 1.** A teammate creates a file named `up` in the repo root (say, a log dump).
What does `make up` print afterward if `.PHONY` were missing, and why does `.PHONY` prevent
it?

**Answer.** Without `.PHONY`, make treats `up` as a *file to build*. The file exists and has
no prerequisites to make it stale, so make prints `make: 'up' is up to date.` and runs
nothing — the stack silently stops being startable. `.PHONY: up` tells make the target is a
command name, not a file, so the recipe runs every time regardless of what's on disk.

**Question 2.** Explain `profile ?= full` and what happens, step by step, when someone runs
`make up profile=events`.

**Answer.** `?=` assigns a default only if the variable isn't already defined. A
command-line assignment like `profile=events` defines it with the highest precedence, so the
`?=` line is skipped. The recipe expands `$(profile)` → `docker compose -f ... --profile
events up -d --wait`, starting kafka and schema-registry only. The `case` line then checks
"events" against `media|full`, doesn't match, so the bucket job is skipped — correct, since
MinIO isn't running.

**Question 3.** `make up` failed with `Error 1` but `make ps` shows everything healthy plus
one `Exited (0)`. What happened, and why would fixing it with `up -d --wait || true` be
worse than our fix?

**Answer.** `--wait` was watching a run-once job container; when it finished (exit 0 — its
definition of success), Compose 5.8 counted a non-running container as a wait failure and
exited 1. `|| true` would swallow that — but also every *real* failure: an unhealthy Kafka,
a crash-looping registry, all would report success, and scripts/CI would march on against a
broken stack. The real fix removes the job from the wait set (own profile) and runs it
explicitly afterward with `compose run --rm`, which preserves genuine exit codes in both
directions.

### Your doubts

> **"I am getting `*** missing separator. Stop.` when running `make help`. Can you help me
> here?"**

That error means exactly one thing, almost always: **a recipe line starts with spaces
instead of a tab**. Make's grammar uses the tab character as its marker for "this line is a
command belonging to the target above"; four spaces look identical on screen but are
different bytes, and make refuses to guess.

The error's full form includes the line number of the first bad line:

```
Makefile:14: *** missing separator.  Stop.
```

Find and fix:

1. **See the invisible:**

   ```
   grep -n '^ ' Makefile     # every line starting with a SPACE - should print nothing
   cat -t Makefile | head    # shows real tabs as ^I at the start of recipe lines
   ```

   Healthy recipe lines look like `^I@echo ...` in `cat -t` output; broken ones show plain
   spaces.

2. **Fix each reported line:** delete the leading whitespace, press the real Tab key once.

3. **Stop the editor doing it again** (the usual culprit — editors default to inserting
   spaces for Tab): in Cursor/VS Code, status bar bottom-right → if it says `Spaces: 4`,
   click → **Indent Using Tabs**, then run **Convert Indentation to Tabs** from the command
   palette to repair the whole file at once. Files *named* `Makefile` normally get tab mode
   automatically; hand-created files sometimes miss the detection.

> **Follow-up: "the Makefile works on one machine but the copy on my other machine gives
> this error."**

How the file traveled is the whole story. **Copying the file itself** (AirDrop, USB, `scp`)
preserves every byte — tabs included. **Copying the text** (select-all → paste into an
editor, or sending through a chat/notes app) almost always converts tabs to spaces: editors
normalize pasted indentation, transports flatten tabs. The file looks identical; the bytes
aren't.

One-command repair on the broken copy (safe for this Makefile, where no legitimate line
starts with a space):

```
perl -i -pe 's/^ +/\t/' Makefile
grep -n '^ ' Makefile    # should print nothing afterwards
make help                # should print the menu
```

Prevention: move files as files, not as text — and once git arrives (Task 0.4),
`git clone` carries exact bytes to every machine and this class of problem disappears.

> **Follow-up 2: "still the same error after the space→tab fix."**

Pasting the broken file revealed why: the recipe lines had **no leading whitespace at all** —
the text transfer had stripped it completely, not converted it. So the space→tab repair had
nothing to replace (`grep -n '^ '` silent, error unchanged). A command line at column 0
isn't a recipe to make; it's an unparseable line → `missing separator`.

Repair for this exact Makefile (every recipe line starts with `@`, `$(COMPOSE)`, or
`docker `, and no non-recipe line does), run in the Makefile's folder:

```
perl -i -pe 's/^(?=\@|\$\(COMPOSE\)|docker )/\t/' Makefile
make help     # should print the menu
```

Diagnosis pattern worth keeping: when the "obvious" fix changes nothing, look at the raw
reality (paste the file, or `od -c` the error line) instead of re-running the fix harder —
the assumption ("tabs became spaces"), not the fix, was wrong ("tabs became nothing").

---

## Lesson 17 — The Makefile grows with the project: the menu is a contract (Step 11)

### Definition

Not a coding step — a mental-model step. The Makefile is **the one place anyone (including
future you) looks to find out how to run things**. Its target *names* form a stable contract
(`make up`, `make test`, `make smoke`), while the recipes behind the names get rebuilt many
times over the project's twenty-five weeks. Placeholders are signposts: the menu shows the
destination before the road is built.

### Why it is needed

Habits, docs, and CI all bind to names. If the names stay stable, nothing that *calls* them
ever has to change — exactly like an interface in Java: callers bind to the signature,
implementations swap underneath. It's also why `docs/local-setup.md` can stay short and stay
true: a doc that says "run `make up`" never rots, while a doc full of 70-character compose
incantations is stale within a month.

### How it works, step by step

1. `test` today prints a placeholder line. In Phase 0 it becomes `mvn verify` for one
   module; later, a dozen modules; later still, Testcontainers suites. Every caller keeps
   typing `make test`, oblivious.
2. Each placeholder carries a comment and prints an honest line naming the task that makes
   it real — running one tells you where you are in the journey.
3. The roadmap: `test` → Phase 0 (first Maven module); `fmt` → Task 0.3 (quality gates that
   fail the build); `ci` → Task 0.4 (git workflow, the remote, and a local CI pipeline);
   `smoke` → Task 1.11 (the Testcontainers harness and test data builders); `seed` →
   Task 3.1 (realistic data volume; gains a `scale=small|full` variable); `cd` → Task 13.4
   (CI/CD pipeline); `demo` → Task 14.3 (demo path and interview preparation).
4. The prescribed exercise: read the Makefile top to bottom and say out loud what each
   target does. Sixteen names; if you can say each sentence without looking, you own the
   file.

### Example — real commands, real output from this machine

The grown menu (three new signposts at the bottom):

```
  make help       this menu
  make up         start the stack and wait for healthy (make up profile=core|events|media|full)
  make down       stop and remove containers; data survives in the volumes
  make reset      DESTRUCTIVE factory reset: containers AND volumes gone
  make logs       follow logs (make logs s=kafka for one service)
  make ps         status of every service
  make psql       interactive SQL shell in Postgres
  make redis-cli  interactive Redis shell
  make topics     list Kafka topics
  make test       run the Java tests
  make fmt        format the code
  make smoke      end-to-end smoke test against the running stack
  make seed       load demo data (will take scale=small|full from Task 3.1 on)
  make ci         run the whole CI pipeline locally
  make cd         deploy pipeline
  make demo       bring up the demo path end to end

$ make ci
placeholder: real in Task 0.4 - git workflow, the remote, and a local CI pipeline
```

### Important terms

| Term | Meaning |
|---|---|
| Contract / stable name | Callers (fingers, docs, CI) bind to `make test`, not to its recipe |
| Placeholder as signpost | A target that names its future instead of apologizing |
| Intention vs implementation | Docs reference intentions; the Makefile maps them to reality |

### Common mistakes

- Renaming targets casually — that breaks every habit, doc, and pipeline bound to the name.
  Recipes are cheap to change; names are expensive.
- Writing setup docs full of raw commands instead of `make` names — they rot immediately.
- Adding targets only when they work. Reserving the name early (as a placeholder) lets
  habits and docs form before the implementation exists.

### Where it is used

- Week 25's CI pipeline will literally run `make ci`; the demo day script runs `make demo`.
- Every mature repo you'll ever join has this shape: look at its Makefile (or `justfile`,
  `package.json` scripts — same idea) to learn what the repo *can do*.

### Link to the task

Step 11 verbatim: "Not a coding step — a mental-model step. The Makefile is the one place
anyone looks to find out how to run things... Keeping every operation behind a named target
is exactly why `docs/local-setup.md` can stay short." Do-this: read the Makefile aloud top
to bottom; note in comments which placeholders become real where (smoke → 1.11, ci → 0.4,
seed → 3.1, cd → 13.4, demo → 14.3). Both done: the comments are in the file, and the
read-aloud walkthrough is in this lesson.

### Do it yourself

1. Add `ci`, `cd`, `demo` placeholder targets to the Makefile; extend `.PHONY` with them;
   add the roadmap comment block and the task-naming echo lines (full text in Lesson 16's
   Makefile plus this lesson's table).
2. Run `make` — the menu should show all sixteen targets. Run `make ci`, `make demo`,
   `make seed` and read each signpost line.
3. The exercise: open the Makefile and say out loud, top to bottom, what each target does
   and which task makes each placeholder real. No peeking.

**Done when:** you can explain every line of the Makefile from memory, and name the task
where each placeholder comes alive.

### Summary for revision

- The Makefile is a contract: names stable for years, recipes rebuilt freely.
- Callers bind to names — like interfaces in Java. `make test` never changes; what it runs
  does.
- Placeholders = roadmap signposts; each names the task that makes it real.
- Roadmap: test→Phase 0, fmt→0.3, ci→0.4, smoke→1.11, seed→3.1 (+scale var), cd→13.4,
  demo→14.3.
- Docs reference intentions (`make up`), so they stay short and never rot.

### Check questions, with answers

**Question 1.** In week 25, `make test` runs hundreds of tests across modules. Which files
changed to get there — and which *callers* of `make test` had to change?

**Answer.** The Makefile's `test` recipe changed (echo → `mvn verify` → multi-module,
Testcontainers...), plus of course the code and tests themselves. The callers changed **not
at all**: your fingers, the docs that say "run `make test`", and the CI pipeline all kept
invoking the same name. That's the contract: implementations swap underneath a stable
signature, exactly like a Java interface.

**Question 2.** Why does hiding every operation behind a named target keep
`docs/local-setup.md` short *and* correct over time?

**Answer.** Short, because the doc only has to say "run `make up`, then `make psql`" —
intentions, not incantations. Correct, because the mapping from intention to current
implementation lives in the Makefile, which is *executed daily* — if it broke, everyone
would notice within hours, unlike a doc that rots silently. The doc delegates the moving
parts to the one file that can't go stale unnoticed.

**Question 3.** What's the argument for shipping a `make cd` target today that only prints a
line, instead of adding it in Task 13.4 when it works?

**Answer.** The menu is the repo's table of contents: shipping the name early makes the
roadmap discoverable (`make` shows where the project is going), reserves a stable name that
docs and habits can bind to before the implementation lands, and running it tells you
exactly where you are ("real in Task 13.4"). Adding names only when they work means every
arrival also demands new docs, new habits, and renaming risk.

### Your doubts

None in this lesson.

---

## Lesson 18 — Prove the document, not the memory (Step 12)

### Definition

Step 12 turns `docs/local-setup.md` into a **proven** document: write the setup guide a
brand-new teammate would follow on day one — then *become* that teammate. Wipe everything
(`make reset`), follow only what the document says, no memory allowed. Every gap found is a
bug **in the document**: fix it, wipe, start again. The step ends when the doc alone, from
nothing, produces a fully healthy stack.

### Why it is needed

Docs rot unless they are executed. A setup guide written from memory contains the author's
unconscious knowledge ("obviously you need the `.env`...") that a newcomer doesn't have. The
only trustworthy setup document is one that has been *followed from zero*. Bonus discovered
tonight: such a document doubles as **disaster recovery** — if the files themselves die, the
documentation resurrects them.

### How it works, step by step

1. Write `docs/local-setup.md` with everything day one needs: **(1)** tools to install on a
   blank Mac (brew, colima + docker + docker-compose, make, the Java toolchain note),
   **(2)** create `deploy/compose/.env` — stressed hard, because it's git-ignored and so
   *missing from every fresh clone*, **(3)** `make up` / `make ps`, **(4)** the
   where-everything-is table (six services, ports, credentials), **(5)** the daily `make`
   menu, **(6)** the storage rule (named volumes for state, bind mounts for config you
   edit), **(7)** a troubleshooting table of every real failure this task produced.
2. `make reset` — containers and volumes gone.
3. Follow the document from the top, doing only what it says.
4. Every gap or wrong instruction: fix the document, wipe, start again.
5. Done when the doc alone ends in a healthy stack — which is also the task's done-when:
   `make reset && make up` healthy in under 90 seconds, `make psql` opens a shell.

### Example — real commands, real output from this machine

**The incident first.** Mid-step, the entire `deploy/` folder and the fresh
`docs/local-setup.md` vanished (a checkpoint rollback fired alongside a rejected command
approval). Two findings turned disaster into a shrug:

- **The running stack didn't care.** Containers run from their *created* state; the YAML is
  only consulted at `up` time. Six healthy services hummed along with their definition file
  deleted — though every `docker compose` command (even `ps`) failed with
  `no such file or directory`.
- **The documentation was the backup.** Every line of the lost files exists in this README;
  all four were rebuilt from it, then proven equivalent with a dry run:

```
$ docker compose -f deploy/compose/docker-compose.infra.yml up -d --dry-run
 Container infra-kafka-1 Running
 Container infra-schema-registry-1 Running
 ...all seven: Running                ← nothing to recreate = rebuilt file ≡ original
```

Checkpoint question 5 ("someone deletes your compose file...") stopped being hypothetical.

**Then the ceremony**, following the document only:

```
=== WIPE ===  make reset
=== make up, timed ===
Bucket created successfully `local/media`.
bucket 'media' ready
make up ... 18.287 total          ← 18 seconds; the budget is 90

--- postgres --- PostgreSQL 17.11 on aarch64 ...      (fresh volume, reinitialized)
--- redis ---    PONG
--- kafka ---    __consumer_offsets  _schemas         (recreated compact by a healthy registry)
--- registry --- []                                   (empty subjects list = alive, fresh)
--- minio ---    [0B] media/ ... console 9001 -> HTTP 200
--- mailpit ---  {"total":0,"unread":0,...}           (empty inbox = fresh volume, correct)
```

Every service answers at the exact address the document advertises.

**One discovery: the orphan.** `make ps` showed a stale `infra-minio-init-1` from *before*
the wipe. The job lives in the `init` profile, inactive during `down`, so Compose didn't
consider its container part of the model — such leftovers are **orphans**. Fix (now in the
Makefile): `down --remove-orphans` on both `down` and `reset`. The *new* job container never
lingers — `compose run --rm` deletes itself.

### Important terms

| Term | Meaning |
|---|---|
| Day-one teammate test | Follow your own doc from nothing, no memory allowed |
| Proven document | A doc that has actually produced a working system from zero |
| `up -d --dry-run` | Shows what `up` *would* do — `Running` everywhere = no drift |
| Orphan container | Container of a service not in the current model (e.g. inactive profile) |
| `--remove-orphans` | `down` flag that also removes those leftovers |

### Common mistakes

- Filling gaps from memory while proving the doc — the whole point is to let the doc fail.
- Writing raw 70-character incantations in the doc instead of `make` names (it rots in a
  month; the menu doesn't).
- Forgetting the `.env` section. It's git-ignored, so every fresh clone lacks it, and
  Compose only *warns* — the failure surfaces far downstream.
- Fixing the doc but not re-running the wipe-and-follow loop afterwards.
- Forgetting job containers live outside `down`'s default scope once they're in their own
  profile — use `--remove-orphans`.

### Where it is used

- Onboarding: this exact pattern is how good teams keep "day one" under an hour.
- Runbooks and disaster recovery: docs you've never executed are hopes, not plans.
- CI bootstrap (Task 0.4) will lean on the same property: a stack that builds itself from
  nothing, deterministically, fast.

### Link to the task

Step 12 verbatim: write the doc, wipe all volumes, follow your own instructions resisting
every urge to fill gaps from memory; fix the document at every gap. "At the end of this step
the document is proven, not merely written." Done-when scoreboard: `make reset && make up`
all-healthy in **18s** (limit 90) — proven twice; `make psql` opens a shell (proven
non-interactively; your fingers do the final check); the six services explained — checkpoint
question 5 below.

### Do it yourself

This one *is* the second-laptop task — the whole point:

1. On the second laptop, open `docs/local-setup.md` and follow it from section 1, top to
   bottom, doing **only** what it says. (First run pulls images — minutes, not seconds.)
2. Each time you hit a gap, a missing prerequisite, or a wrong instruction: stop, fix the
   document, `make reset`, start again from the top.
3. Finish with the interactive checks under your own fingers:

   ```
   make psql        # select 1;  then \q
   make redis-cli   # PING  -> PONG, then quit
   ```

4. Open http://localhost:8025 (Mailpit) and http://localhost:9001 (MinIO console,
   minio/minio12345) in a browser.

**Done when:** following only the document, from wiped volumes, ends in six healthy services
in under 90 seconds — and you can answer the eight checkpoint questions below out loud.

### Summary for revision

- Write the doc → wipe → follow only the doc → fix the doc at every gap → repeat. Proven,
  not merely written.
- The doc says intentions (`make up`); the Makefile keeps them true; that's why it stays
  short.
- `.env` is git-ignored: every fresh clone needs section 2. Compose only warns if missing.
- Containers outlive their YAML; any compose *command* needs the file. Docs = backup.
- `up -d --dry-run` proves a rebuilt/edited file matches running reality (`Running`, no
  recreates).
- Orphans = containers of services outside the current model; `down --remove-orphans`.
- Scoreboard: 18s to healthy (budget 90), psql opens, six services explainable.

### Check questions, with answers

**Question 1.** The compose file was deleted while six services were running. Why did they
keep running, and what *would* have revealed the loss?

**Answer.** Containers run from the state they were *created* with; the Docker daemon holds
everything it needs, and the YAML is only read when Compose commands run. So the stack
hummed on, oblivious. The loss shows the moment anything consults the file: every
`docker compose -f ...` invocation — `up`, `down`, even `ps` — fails with
`no such file or directory`, as `make reset` did.

**Question 2.** After rebuilding the compose file from documentation, what did
`up -d --dry-run` prove, and how?

**Answer.** Compose resolves the rebuilt file (YAML + `.env`) and compares every service's
desired definition against the running containers, reporting what it *would* do. Every
container reported `Running` — no `Recreate` anywhere — proving the rebuilt file is
operationally identical to the one the stack was created from. Comments and whitespace
can differ; everything Docker acts on matched.

**Question 3.** Why did `make reset` miss the old `minio-init` container, and what's the
fix?

**Answer.** The job sits in the `init` profile, which isn't active during `down`
(`COMPOSE_PROFILES=full` doesn't include it), so Compose didn't count its container as part
of the model — it became an *orphan* and was skipped. Fix: `down --remove-orphans` (now on
both `make down` and `make reset`), which also removes containers of services absent from
the active model.

### Your doubts

None in this lesson.

---

## Task checkpoint — answer out loud, from memory (model answers)

**1. What is the difference between an image, a container, and a volume? Which survives
`docker compose down`?**

An **image** is the read-only template (program + filesystem, e.g. `postgres:17-alpine`). A
**container** is a running (or stopped) *instance* of an image plus a thin writable layer —
disposable by design. A **volume** is Docker-managed storage living *outside* any container,
attached at a path. `down` deletes containers and the network; **images and volumes
survive**. Only `down -v` removes volumes too.

**2. Why is `depends_on` on its own not enough to stop your application starting before
Postgres can accept connections?**

Bare `depends_on` orders **creation**, not readiness: "start that container's process
first." A database needs seconds between process-exists and accepting-connections, and
anything connecting in that gap fails. Closing it needs a **healthcheck** on Postgres (a
real probe like `pg_isready`) plus `condition: service_healthy` on the dependent — "don't
start me until the probe passes." Measured here: 5 disconnect-spam lines when racing, 0 with
the condition.

**3. You run `make up` and Kafka never becomes healthy. First three checks, in order?**

One: `make logs s=kafka` — read the actual error (bad config, `CLUSTER_ID` mismatch against
an old volume, OOM-killed JVM). Two: the healthcheck itself —
`docker inspect -f '{{json .State.Health}}' infra-kafka-1` — is the probe failing or merely
timing out (JVM probes need generous timeouts), and what does its output say? Three:
resources and state — `docker stats` (is the VM's 8 GiB exhausted — exit 137?), and if the
volume predates config changes, suspect preserved-but-incompatible state: `make reset` as
the controlled experiment.

**4. Why does this project run Kafka in KRaft mode rather than with ZooKeeper?**

ZooKeeper was a *second distributed system* Kafka historically needed for metadata and
controller election — more containers, more config, more failure modes. KRaft folds that
into Kafka itself: our single node is both `broker` and `controller` with a one-voter
quorum, one container instead of two. And upstream, ZooKeeper mode is gone in Kafka 4 —
KRaft is simply what Kafka is now.

**5. Someone deletes your `docker-compose.infra.yml`. Which six services go back, and what
is each one for?**

(Proven live tonight.) **Postgres 17** — system of record; relational truth. **Redis 7** —
speed layer; cache/sessions that may be lost and rebuilt. **Kafka (KRaft)** — the event log
services use to talk asynchronously. **Schema Registry** — contract-keeper: stores message
schemas in Kafka's `_schemas` topic, enforces compatibility so producers can't silently
break consumers. **MinIO** (+ its run-once `minio-init` bucket job) — S3-compatible object
storage for files, bucket `media`. **Mailpit** — fake SMTP catching all outgoing mail, web
inbox on 8025, so no real email escapes a dev machine.

**6. You edit `nginx.conf` on your Mac and the proxy container sees the change with no
rebuild. What mechanism, and when is a named volume right instead?**

A **bind mount**: `-v /host/path:/container/path:ro` maps the host file into the container —
same file, two viewpoints, saves visible instantly (the process still needs a reload signal
to re-read it). A **named volume** is right for *state the program writes* — Postgres data,
Kafka segments — Docker-managed, outliving containers, never hand-edited. Who writes the
file? You → bind mount. The program → volume.

**7. Why does the `mc` bootstrap container keep `restart: no` while long-running services
get `unless-stopped`?**

Exiting is the job's *success*: create the bucket, exit 0. Any restart policy would
resurrect it forever — a container whose goal is to end must be allowed to end. Servers are
the opposite: their job is to run indefinitely, so `unless-stopped` revives them after
crashes (and reboots) unless a human deliberately stopped them. And it must be `"no"`
*quoted* — bare `no` is YAML's boolean false.

**8. Your database password lives in `.env`. Why fine here, not in production — and which
two later tasks hold the production answer?**

Here it's a throwaway value on a dev laptop; `.env` is **convenience, not secrecy** — it
keeps credentials out of git and lets machines differ, but it's plaintext on disk, visible
to `docker compose config` and `docker inspect`. Production needs real secret management:
encrypted stores, runtime injection, rotation, audit — arriving in **Task 2.4 — application
security hardening and auditing** and **Task 13.3 — scaling, configuration, and
deployment**.

---

## Task 0.1 — closed

**Done-when, measured:** `make reset && make up` → six healthy services in **18 seconds**
(budget: 90) · `make psql` opens a SQL shell · all six services explainable (checkpoint 5).

**Artifacts:** `deploy/compose/docker-compose.infra.yml` (7 services, 5 named volumes,
network `infra-net`, profiles core/events/media/init/full) · `deploy/compose/.env`
(git-ignored; `COMPOSE_PROFILES=full` + credentials) · `.gitignore` · `Makefile` (16
targets, 7 roadmap placeholders) · `docs/local-setup.md` (proven from wiped volumes) · this
README (18 lessons, every output real).

Remaining for you: the second-laptop pass using only `docs/local-setup.md`, and the eight
checkpoint questions out loud.

---

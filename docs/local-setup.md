# Local setup

Day-one guide: from a blank Mac to the full local stack. Follow it top to bottom; don't
fill gaps from memory. If a step doesn't work, the document is wrong — fix the document.
(The deep story behind every choice lives in
`0.1-local-infrastructure-in-one-command/local-infrastructure.md`.)

## 1. Install the tools (once)

```sh
# Homebrew (macOS package manager) - from https://brew.sh
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Container runtime + CLIs (Colima is the lightweight Docker Desktop alternative)
brew install colima docker docker-compose

# make ships with Apple's command-line tools; if `make --version` fails:
xcode-select --install

# Java toolchain (needed from Task 0.2 on, not for the infra itself):
# SDKMAN needs bash 4+ (macOS ships 3.2), then Java 21 pinned by .sdkmanrc
brew install bash
curl -s "https://get.sdkman.io" | bash
sdk env install   # run inside the repo; installs the pinned Java
```

Start the Linux VM that all containers share (once per boot, ~8 GiB for the stack):

```sh
colima start --cpu 4 --memory 8
```

## 2. Create the credentials file (once per machine)

`deploy/compose/.env` is git-ignored, so a fresh clone does NOT have it. Create it:

```sh
cat > deploy/compose/.env <<'EOF'
COMPOSE_PROFILES=full

POSTGRES_PASSWORD=dev
MINIO_ROOT_USER=minio
MINIO_ROOT_PASSWORD=minio12345
EOF
```

(Convenience, not secrecy — these are local throwaway values.)

## 3. Bring the stack up

```sh
make up        # starts everything, waits until healthy, creates the MinIO bucket
make ps        # every service should say Up ... (healthy)
```

First run pulls images (a few minutes); afterwards `make reset && make up` reaches
all-healthy in well under 90 seconds. Only need databases today? `make up profile=core`.

## 4. Where everything is

| Service | What it's for | Port(s) on localhost | Credentials |
|---|---|---|---|
| Postgres 17 | the system of record (SQL) | 5432, db `app` | `postgres` / `dev` |
| Redis 7 | cache/speed layer | 6379 | none |
| Kafka 4 (KRaft) | event log between services | 9092 (from containers: `kafka:29092`) | none |
| Schema Registry | contract-keeper for Kafka messages | http://localhost:8081 | none |
| MinIO | S3-compatible file storage, bucket `media` | 9000 API, 9001 web console | `minio` / `minio12345` |
| Mailpit | fake SMTP that catches all outgoing mail | 1025 SMTP in, http://localhost:8025 UI | none |

## 5. Daily commands

```sh
make            # the menu
make up         # start + wait healthy (profile=core|events|media|full)
make down       # stop containers; DATA SURVIVES
make reset      # DESTRUCTIVE: containers + volumes wiped
make logs       # follow logs (make logs s=kafka for one service)
make psql       # SQL shell in Postgres    (quit: \q)
make redis-cli  # Redis shell              (quit: quit)
make topics     # list Kafka topics
```

## 6. The storage rule

- **Named volumes for state.** Bytes the programs write (Postgres, Redis, Kafka, MinIO,
  Mailpit data) live in Docker-managed volumes (`pg-data`, `redis-data`, `kafka-data`,
  `minio-data`, `mailpit-data`). They survive `make down`; only `make reset` deletes them.
- **Bind mounts for config you edit.** Files you open in your editor (e.g. nginx config
  later) are mounted from the repo into the container, read-only; an edit on the host is
  visible instantly, at most one reload signal away.

## 7. When something is off

| Symptom | Meaning | Fix |
|---|---|---|
| `Cannot connect to the Docker daemon` | VM not running | `colima start --cpu 4 --memory 8` |
| `Bind for 0.0.0.0:5432 failed: port is already allocated` | another container/app owns the port | `docker ps` → stop the old container |
| warnings: `"POSTGRES_PASSWORD" variable is not set` | missing `.env` | redo section 2 |
| `make up` → `Error 1` though all healthy, one `Exited (0)` | a run-once job was inside `--wait`'s scope | jobs live in the `init` profile; `make up` runs them separately — keep it that way |
| schema-registry restarting, logs full of `Node -1 disconnected` | it started before Kafka was ready (race) | ensure its `depends_on` has `condition: service_healthy`; then `make down && make up` |
| registry crash-loop: `cleanup.policy ... expected 'compact' but it is delete` | a lost race let Kafka auto-create `_schemas` wrongly; the volume preserved it | `docker exec infra-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic _schemas` then `make up` |
| stack behaves impossibly weird | state from old experiments | `make reset && make up` — 90 seconds to factory-fresh |

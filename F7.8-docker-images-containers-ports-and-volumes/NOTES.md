# NOTES — container memory check (Task F7.8, subtask 9)

Measured 2026-11-18 with `docker stats --no-stream`, three containers running.

## The budget

Docker here runs inside a hidden Linux VM (Colima) with **2 GiB of memory**.
That whole budget is what the `LIMIT` column shows (1.913 GiB — 2 GiB minus the VM's own
overhead). Every container shares this one pot; no per-container limits are set.

## What each container uses

| Container | Image | Memory used | Share of budget |
|---|---|---|---|
| `pg` | postgres:17-alpine | 46.41 MiB | 2.37 % |
| `pg17` | postgres:17-alpine | 18.59 MiB | 0.95 % |
| `cache` | redis:7-alpine | 4.62 MiB | 0.24 % |
| **Total** | | **≈ 70 MiB** | **≈ 3.6 %** |

## Verdict

- **Most expensive container: `pg`** (46.41 MiB). Both Postgres containers run the same
  image, but `pg` holds the real F5/F6 databases (13 zenith tables) and has served real
  queries, so it has grown bigger caches than the nearly-empty `pg17`.
- Redis idles at under 5 MiB — famously light.
- Nothing is anywhere near over budget: three containers use about 70 MiB of 1958 MiB.
  There is room for the full F7.9 compose stack (app + db) many times over.

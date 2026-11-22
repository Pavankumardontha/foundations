# F7.5 — Measured numbers (subtasks S3 and S4)

Measured 2026-11-17 on the scratch app: Spring Boot 3.5.16, Java 21, in-memory H2,
246 beans in the full context. Each test measured in its own fresh `mvn test -Dtest=...` run.

## S3 — the same assertion, two ways

The assertion: `GET /events/{id}` returns 404 with the F6.8 error body for an unknown id.

| Test class | Context | `Started ... in` | Total `mvn test` time |
|---|---|---|---|
| `L4FullContext404Test` | `@SpringBootTest` + `@AutoConfigureMockMvc` | **1.852 s** | **3.793 s** |
| `L8WebSlice404Test` | `@WebMvcTest(EventController.class)` + `@MockitoBean` | **0.925 s** | **2.761 s** |

Context size: 246 beans (full) vs 129 beans (web slice).
Database: started and schema created (full) vs absent (slice).
Startup gap per test class: **≈ 0.9 s** — on a toy app with an in-memory database.

## S4 — the consequence, said out loud

- On this toy app: 300 test classes × 0.9 s gap ≈ **4.6 minutes** of pure extra startup.
- On a real backend the full context costs 10–30 s (migrations, containers, hundreds of
  beans) while the web slice stays near 1 s. The same 300 classes then cost roughly
  **50–150 minutes against ~5 minutes**.
- A suite that slow stops being run, and a suite that is not run protects nothing.
  That is why slices exist. Reach for the narrowest context that can prove the statement.

## The embedded database is not my real database (S7)

My `@DataJpaTest` runs prove "works on H2", not "works on Postgres". Checked for real:
Postgres's `insert ... on conflict (id) do nothing` fails on H2 with
`JdbcSQLSyntaxErrorException: Syntax error ... [*]on conflict`. Portable JPA (derived queries,
JPQL) is safe to trust; native Postgres SQL, Postgres types (jsonb, arrays), locking tricks
(`skip locked`), and migration DDL are not — H2 gives false alarms (red on H2, fine on
Postgres) or false confidence (green on H2, different on Postgres). Fix later: Testcontainers
(a real Postgres in Docker for tests).

## Side facts from the same session

- One whole-suite run (9 tests, 6 Spring test classes) started only **3 contexts**
  thanks to context caching, finishing in 4.3 s total.
- Inside that shared run, `L6BeanCountTest` "started" in 0.211 s — the same class costs
  1.953 s in a fresh run. Warm JVMs and cached contexts flatter the numbers, so honest
  per-class measurements need fresh runs.

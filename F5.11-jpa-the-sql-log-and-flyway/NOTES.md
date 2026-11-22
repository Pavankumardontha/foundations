# NOTES.md — F5.11

## Who created the repository implementation? (subtask B)

I wrote only this, and no class anywhere implements it:

```java
public interface EventRepository extends JpaRepository<Event, UUID> {
}
```

**Who created it:** Spring Data JPA, at application startup.

1. Spring Data scanned the packages and found the interface — the startup log reported:
   `Finished Spring Data repository scanning in 15 ms. Found 1 JPA repository interface.`
2. It generated a stand-in object (a JDK dynamic proxy) that implements `EventRepository`.
   Printing its class shows a name no human wrote: `jdk.proxy2.$Proxy93`.
3. Every call on that proxy is carried out by Spring Data's prewritten generic class
   `org.springframework.data.jpa.repository.support.SimpleJpaRepository`, bound to the
   `Event` entity's mapping, which talks to Hibernate, which talks to JDBC.
4. The proxy was registered as a Spring bean named `eventRepository`, ready for injection.

Evidence (real output from the proof runner):

```text
>>> repository bean injected: true
>>> its runtime class: jdk.proxy2.$Proxy93
>>> the worker behind it: org.springframework.data.jpa.repository.support.SimpleJpaRepository
```

One sentence for revision: **I declared the promise; Spring Data JPA built the worker at
startup and handed it to me as a bean.**

## Why "make everything eager" is not the fix (subtask G)

The one sentence: **On a list endpoint that only needs names, eager loading fetches every
event's full price-tier collection on every request — one extra query per row that the
response never uses — so the endpoint pays the N+1 unconditionally, with no suspicious code
line left to point at and no way to opt out for just one query.**

Evidence from flipping `fetch = EAGER` on `Event.priceTiers` for one run:

- `findById(...)` became a `left join price_tier` — tiers fetched even when unneeded.
- The name search (code never touches tiers) fired 1 + 3 statements instead of 1.
- The N+1 demo still fired 6 statements — but all *before* the loop ran. Eager did not remove
  the N+1; it made it automatic, constant, and impossible to see as a "touch" in code.

Reverted to lazy immediately after measuring.

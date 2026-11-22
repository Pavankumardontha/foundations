# NOTES — F3.8 Immutability and defensive copying

`private final List<X> items` prevents the field being pointed at a different list.
It does nothing about adding items.

(`final` locks the reference box, not the object it points to. Proof: `Concept4Error.java`
fails to compile on reassignment; `Concept4.java` mutates through the final field and prints
`[A1, B2, C3]`. Real immutability = `final` field + immutable object behind it, e.g.
`List.copyOf` on the way in.)

## The project rule

**Value objects are always immutable. Entities change only through intention-revealing
methods such as `hold(seatIds)` or `publish()` — never through setters.**

- Value object = nothing but its data, no identity (seat id, money, `SeatMap`). "Change" =
  persistent update (`withSeatAdded` returns a new instance).
- Entity = identity that survives change (an order stays order O-17 across state changes).
  Every change goes through a named, guarded method that enforces the business rule and
  rejects illegal transitions (`Concept8.java`).

Forward link to the concurrency track: an immutable object needs no locking — race conditions
require a writer, and an immutable object admits no writers. Every thread can only read, and
simultaneous reads are always safe.

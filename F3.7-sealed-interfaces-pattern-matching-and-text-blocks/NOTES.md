# NOTES — F3.7 Sealed interfaces, pattern matching, and text blocks

**A sealed interface says "these are all the outcomes." An enum says "these are all the
values." An open interface says "anyone may add more."** Choosing deliberately between the
three is a senior habit.

## The two-question decision

1. **Do occurrences carry data?** No → enum. Yes → question 2.
2. **Is the set closed by the nature of the domain?** Yes → sealed interface (reward:
   exhaustive, `default`-free switches — new cases become compile errors, not production
   bugs). No → open interface (reward: new implementations plug in without touching existing
   code; price: no exhaustiveness, correctly).

## Proof points

- `HoldStatus.REJECTED == HoldStatus.REJECTED` is `true` — an enum constant is one shared
  value for the whole program; there is nowhere to put *this* request's reason.
- `new Rejected("sold out") != new Rejected("payment failed")` — sealed-type instances are
  types carrying per-occurrence data. (`Concept7.java`)

## Applied to this project

- Report compression `ZIP / GZIP / NONE` → enum.
- `HoldResult` = `Granted / Rejected / PartiallyGranted` → sealed interface + records.
- `DiscountRule`, notification channels → open interface.

## The guard rail that makes sealing worth it

Over a sealed type, a `switch` with **no `default`** is compiler-verified exhaustive. Adding
a permitted type fails the build at every unhandled switch, with locations
(`error: the switch expression does not cover all possible input values`, `Concept2Broken`).
Writing `default` "to be safe" turns that guarantee off.

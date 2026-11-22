# NOTES — F6.2

## S2 — the three-point comparison (one sentence each, in your own words)

- Honesty: (your sentence here)
- Immutability: (your sentence here)
- Testability: (your sentence here)

## S3 — the pair, kept

```java
// Discouraged: field injection
@Service
class SeatHoldService {
    @Autowired private HoldRepository holds;   // cannot be final, invisible from outside
}

// Preferred: constructor injection
@Service
class SeatHoldService {
    private final HoldRepository holds;

    SeatHoldService(HoldRepository holds) {    // one constructor, so no @Autowired needed
        this.holds = holds;
    }
}
```

The discouraged exhibits (`FieldSeatHoldService`, `SetterSeatHoldService`) lived in
`scratch-app/src/main/java/com/zenith/holds/` during the task and were deleted in the
final cleanup — the done-when requires no `@Autowired` on any field. This written pair
is the kept record; the preferred form lives on as `SeatHoldService.java`.

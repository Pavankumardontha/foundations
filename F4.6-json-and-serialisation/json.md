# Task F4.6 — JSON and serialisation

## Lesson 38 — JSON, and the invisible translator between your objects and the wire

### What is it?

**JSON** is a text format for structured data: objects in braces, lists in brackets,
strings in quotes. It is what travels in request and response bodies. Around it, three
words: **serialisation** — turning an object in memory into text to send over the wire;
**deserialisation** — the reverse; **Jackson** — the library that does both in Spring
Boot. You never call it directly; it is wired in by default — which is why the code has
never mentioned JSON even though every demo has been full of it.

### Why do we need it?

An object in a Java program's memory cannot travel: it is a tangle of references
meaningful only inside that one process. The two sides need a *neutral text form* both
can produce and parse. JSON won that job: human-readable enough to debug by eye, strict
enough for machines, natively understood by every client language.

### How does it work?

JSON has exactly six kinds of value:

| JSON | Looks like | Java source |
| --- | --- | --- |
| object | `{"name":"gig"}` | a record or class — field names become keys |
| array | `[1, 2, 3]` | a `List` |
| string | `"hello"` | `String` |
| number | `42`, `9.5` | `int`, `long`, `double`… |
| boolean | `true`, `false` | `boolean` |
| null | `null` | a `null` reference |

Values nest; that is the entire grammar.

The *when*: on the way out, the handler returns a plain Java object; **after** it
returns, Jackson walks the fields and writes JSON text (and `Content-Type:
application/json` is set). On the way in, Jackson runs **before** the handler: it parses
the body and constructs the object the `@RequestBody` parameter receives. Handlers only
ever touch real objects; JSON exists purely at the boundary.

### The demo

The record and handler — nothing in them mentions JSON:

```java
record HelloResponse(String message, int id) {}

@GetMapping("/hello")
HelloResponse hello() {
    return new HelloResponse("hello", 1);
}
```

```text
=== 1) a Java record goes out as JSON ===
GET /hello
{"message":"hello","id":1}

=== 2) JSON comes in, a Java record gets built ===
POST /events   {"name":"gig"}
HTTP/1.1 201 
{"name":"gig"}

=== 3) a list of records becomes a JSON array of objects ===
GET /orders
[{"id":1,"item":"2 tickets"},{"id":2,"item":"1 ticket"}]

=== 4) all the JSON value kinds in one response ===
GET /events/gig/seats
[{"id":"H1","held":false},{"id":"H2","held":true},{"id":"J1","held":false}]

=== 5) broken JSON in: the translator rejects it before my code runs ===
POST /events   {"name": }
HTTP/1.1 400 
```

### Walkthrough

1. **Capture 1: serialisation caught in the act.** The handler returned a Java object;
   the wire shows text — component names became keys, `String` became a JSON string,
   `int` a bare number. Jackson converted after the handler returned, uninstructed.
2. **Capture 2: deserialisation.** The wire carried text; the handler's
   `@RequestBody ToyEvent` parameter arrived as a ready-made record. Parsing happened
   before the first handler line ran.
3. **Captures 3–4: the grammar nesting.** A `List<Order>` became an array of objects;
   the seats body shows strings and booleans inside objects inside an array.
4. **Capture 5: where the translator stands.** `{"name": }` is not JSON; the `400` came
   from Jackson's parser *at the door* — the handler never executed. The `400` from the
   status-codes task now has an author with a name.

The task's notebook question — *who does the work, and at what moment?* — answered:
**Jackson, at the two boundaries.** Text→object before the handler runs; object→text
after it returns. Your code lives in object-land.

### Where is it used?

Every body in every endpoint. The **DTO** vocabulary row belongs here: the records
written for requests and responses (`HelloResponse`, `ToyEvent`, `Order`) are Data
Transfer Objects — small classes whose only job is to describe a wire shape, kept
separate from internal domain classes so the two can change independently.

### Common mistakes

- **Building JSON by hand.** String concatenation does badly what Jackson does perfectly.
  Return objects.
- **Thinking the JSON *is* the object.** It is a text snapshot; the name mapping is a
  configurable convention (next lesson).
- **Forgetting the translator runs before validation.** An unparseable body means the
  handler — and all its checks — never runs: Jackson's `400`, not your `422`.
- **Confusing JSON's one number kind with Java's many.** The bridge between them is where
  the money and timestamp bugs live (coming lessons).

### Summary

- Six value kinds, nesting freely — the whole grammar.
- Serialisation after the handler returns; deserialisation before it runs; Jackson does
  both, uninvited and unthanked.
- Handlers touch objects only; JSON exists at the boundary.

### Practice questions

**Question 1.** A Spring Boot handler is declared as
`ToyEvent create(@RequestBody ToyEvent event)` where `ToyEvent` is
`record ToyEvent(String name) {}`. A client sends `POST /events` with the body
`{"name": }` and gets `400 Bad Request`. The developer adds a null-check as the first
line of the handler, re-deploys, and is confused that the `400` still looks exactly the
same and their log line never prints. Explain the order of operations that makes the
null-check unreachable for this request.

**Answer.** Deserialisation happens *before* the handler runs: Spring hands the body text
to Jackson, Jackson tries to parse `{"name": }`, and fails — a value is missing after the
colon, so this is not JSON at all. The failure happens at the parsing boundary, Spring's
error handling answers `400`, and the handler is never invoked — which is why the new
first line, and its log statement, cannot execute no matter how many times this request
is sent. The null-check guards a different case: a body that *is* valid JSON but lacks
the field — `{}` — would parse fine, produce a record whose `name` is `null`, reach the
handler, and there the null-check would finally earn its keep (answering `422`, since the
request parsed but is invalid on its own terms).

**Question 2.** For the Java record `record Seat(String id, boolean held) {}`, a handler
returns `List.of(new Seat("H1", false), new Seat("H2", true))`. Write the exact JSON text
that crosses the wire, name who produces it and when (relative to the handler running),
and identify which of JSON's six value kinds appear in it.

**Answer.** The wire carries:
`[{"id":"H1","held":false},{"id":"H2","held":true}]`. It is produced by Jackson
(invoked by Spring), *after* the handler has returned its `List<Seat>` — the handler
never sees or touches this text. Four of the six value kinds appear: an **array** (the
outer brackets, from the `List`), **objects** (the braces, one per record), **strings**
(`"H1"`, `"H2"`, from the `String id`), and **booleans** (`false`, `true`, from the
`boolean held`). No numbers and no nulls appear in this particular body.

---

## Lesson 39 — @JsonProperty, @JsonIgnore, and the promise you make with field names

### What is it?

Two everyday annotations controlling the wire shape, and one rule governing the next six
months. **`@JsonProperty("starts_at")`** renames a field *on the wire* without renaming
it in Java. **`@JsonIgnore`** removes a field from the wire entirely. The rule: **the
JSON field names are a promise to every client** — renaming one is a breaking change,
even though the Java compiles.

### Why do we need it?

The Java name and the wire name serve different masters. Java names follow Java
conventions (`startsAt`) and are yours to refactor; wire names follow the API's published
convention (often `starts_at`) and belong to *everyone who ever parsed them*. Without
`@JsonProperty` the two are chained — renaming a Java field silently renames the wire,
and distant software breaks. And some fields must never travel: internal notes, password
hashes. `@JsonIgnore` is the fence.

### How does it work?

Default: Jackson derives JSON keys from component names. The annotations override, both
directions:

- `@JsonProperty("starts_at")` on component `startsAt`: serialisation writes
  `starts_at`; deserialisation looks for `starts_at` and binds it. One declaration, both
  directions.
- `@JsonIgnore`: serialisation skips it; deserialisation ignores attempts to set it.

Sharp edge: after renaming, the *Java* name means nothing on the wire. A client sending
`"startsAt"` is sending an unknown field — and Jackson's default is to ignore unknown
fields *silently*.

### The demo

```java
record ShowTime(
        @JsonProperty("starts_at") String startsAt,  // wire name != Java name
        @JsonIgnore String internalNote,             // never leaves the server
        String venue) {}

@GetMapping("/showtime")
ShowTime showtime() {
    return new ShowTime("2026-07-14T19:30:00Z", "VIP list in drawer 3", "Grand Hall");
}

@PostMapping("/showtimes")
ShowTime createShowtime(@RequestBody ShowTime st) {
    return st;                                       // echo what Jackson built
}
```

```text
=== 1) the record has THREE fields; count what the wire shows ===
GET /showtime
{"starts_at":"2026-07-14T19:30:00Z","venue":"Grand Hall"}

=== 2) sending the wire name starts_at: Jackson maps it to startsAt ===
POST {"starts_at":"2026-08-01T20:00:00Z","venue":"Little Hall"}
{"starts_at":"2026-08-01T20:00:00Z","venue":"Little Hall"}

=== 3) sending the JAVA name startsAt instead: silently ignored, field null ===
POST {"startsAt":"2026-08-01T20:00:00Z","venue":"Little Hall"}
HTTP/1.1 200 
{"starts_at":null,"venue":"Little Hall"}
```

### Walkthrough

1. **Three fields in Java, two on the wire.** `starts_at` renamed, `venue` default, and
   `internalNote` — "VIP list in drawer 3" — does not exist outside the process. Not
   empty, not null: *absent*.
2. **The rename works inbound too.** One annotation governs both directions.
3. **Capture 3 is the trap, live.** A client read the *Java* record and sent `startsAt`.
   Jackson found no component with that wire name, ignored it without complaint, built
   the record with `startsAt = null`, and answered `200` — a silent hole in the data. A
   showtime with no start time is now in the system. A later lesson turns on
   `FAIL_ON_UNKNOWN_PROPERTIES`, which makes this a loud failure instead.

The contract rule, concrete: suppose a refactor changes the wire key `starts_at` to
`startsAt`. The code compiles; Java tests pass. And every client that ever parsed
`starts_at` — mobile app, frontend, partner integration — reads `null` from that moment
on: capture 3 inflicted on everyone. The compiler cannot see clients. **Published field
names are frozen promises** — this is why DTOs exist as a separate layer: internal
classes stay free to change because the wire shape is pinned elsewhere.

### Where is it used?

Everywhere wire convention differs from Java's (snake_case APIs, `@JsonProperty` on
nearly every DTO field) and everywhere an object holds something private (`@JsonIgnore`
on password hashes, internal flags). Once Phase 1 publishes an event's JSON shape, those
names outlive any refactor.

### Common mistakes

- **Renaming a published field because the Java name changed.** The breaking change the
  compiler cannot catch. Pin wire names; refactor Java behind them.
- **Returning domain entities instead of DTOs.** Forget `@JsonIgnore` once and an
  internal field leaks to every client. A DTO leaks nothing by construction.
- **Assuming clients know your Java names.** Document the wire shape, not the record.
- **Trusting the silence.** Capture 3's `200` looked like success while losing data.

### Summary

- `@JsonProperty` pins the wire name; `@JsonIgnore` hides a field; both work in both
  directions.
- Unknown incoming names are silently dropped by default — fields quietly become `null`.
- Published JSON names are a contract; renaming is a breaking change even when Java
  compiles.

### Practice questions

**Question 1.** A record is declared as
`record Ticket(@JsonProperty("seat_number") String seatNumber, @JsonIgnore String barcodeSecret, String section) {}`
and a handler returns `new Ticket("H7", "SECRET-991", "Balcony")`. Write the exact JSON
the client receives. Then say what happens when a client `POST`s
`{"seatNumber":"J2","section":"Stalls"}` to an endpoint accepting this record — which
fields of the resulting Java object are populated, and what status does the request get
under Jackson's defaults?

**Answer.** The client receives `{"seat_number":"H7","section":"Balcony"}` — the renamed
key carries the seat, `section` rides under its default name, and `barcodeSecret` is
absent entirely, because `@JsonIgnore` keeps it off the wire. For the `POST`: the
incoming key `seatNumber` matches nothing — the record's wire name for that component is
`seat_number` — so under Jackson's defaults it is silently ignored and the component
`seatNumber` is `null`; `section` binds normally to `"Stalls"`; `barcodeSecret` is `null`
(ignored for input too). The request succeeds — `200`/`201` as the handler decides — with
no error anywhere: a ticket with no seat number entered the system silently. (The strict
`FAIL_ON_UNKNOWN_PROPERTIES` setting would instead fail fast on the unknown
`seatNumber`.)

**Question 2.** A backend team renames a published response field from `starts_at` to
`beginsAt` during a refactor, because the new Java name reads better. Everything
compiles; all the team's own Java tests pass; the deploy is green. Explain why the mobile
app's event screens all show a blank date that evening, why no server-side dashboard
caught it, and what two-part practice (one annotation, one rule) would have prevented the
incident.

**Answer.** The mobile app was written against the published contract: it parses
`starts_at` out of every event body. After the deploy, that key no longer exists — the
server now sends `beginsAt` — so the app's parser finds nothing, gets the equivalent of
null, and renders a blank date on every screen. No dashboard caught it because nothing
failed in protocol terms: every request still returned `200` with valid JSON; the error
rate stayed flat; the break happened inside the *meaning* of the body, which
infrastructure cannot see — the same invisible-to-monitoring failure mode as stale caches
and lying 200s. The two-part prevention: pin every published wire name with
`@JsonProperty`, so Java refactors can never touch the wire by accident; and honour the
contract rule — published field names are frozen promises, so a genuinely needed rename
is a *versioned, breaking* change (a `v2`, or an additive field kept alongside the old
one), never a silent edit.

---

## Lesson 40 — Timestamps: Instant, LocalDateTime, and why everything is stored in UTC

### What is it?

The three Java types for time and their wire format. **`Instant`**: a single moment on
the world's timeline, held in **UTC** — the single reference timezone everything is
stored in, so no stored value depends on where a server happens to be.
**`LocalDateTime`**: a date and time with *no* timezone — "7:30pm" without saying where;
ambiguous by design. **`LocalDate`**: a date with no time at all. On the wire, all three
travel as **ISO-8601** — `2026-07-14T19:30:00Z`, where the `Z` means UTC.

### Why do we need it?

"Why is the timestamp five hours out" is one of the three most common bugs in a first
backend job; on a ticketing platform it means people arriving after the concert ended.
The bug is almost always the same: a time *without a place* got interpreted twice, in two
different places.

### How does it work?

**"7:30pm on July 14th" is not a moment in time** — it happens in Kolkata nine and a half
hours before it happens in New York. To name a moment you must pin the place.

- `Instant` *is* the pin: the moment in UTC, with the `Z` travelling on the wire. Any
  server anywhere reads back the same moment.
- `LocalDateTime` is deliberately unpinned — no `Z`, no offset. Whoever reads it must
  *assume* a timezone, and two readers assuming differently get two different moments.

The rule: **store and transmit moments as `Instant`, in UTC; convert to local time only
at the display edge.** `LocalDateTime` is for the rare "wall-clock time without a place"
meanings; `LocalDate` for date-only facts.

### The demo

```java
record TimeDemo(Instant instant, LocalDateTime localDateTime, LocalDate localDate) {}

@GetMapping("/time-demo")
TimeDemo timeDemo() {
    return new TimeDemo(
        Instant.parse("2026-07-14T19:30:00Z"),           // a moment, pinned to UTC
        LocalDateTime.parse("2026-07-14T19:30:00"),      // "7:30pm" ... where?
        LocalDate.parse("2026-07-14"));                  // a date, no time at all
}
```

```text
=== 1) the three time types on the wire ===
{"instant":"2026-07-14T19:30:00Z",
 "localDateTime":"2026-07-14T19:30:00",
 "localDate":"2026-07-14"}
```

The ambiguity, measured in plain Java — two servers reading the *same* unpinned text:

```java
LocalDateTime show = LocalDateTime.parse("2026-07-14T19:30:00");
Instant inKolkata = show.atZone(ZoneId.of("Asia/Kolkata")).toInstant();
Instant inNewYork = show.atZone(ZoneId.of("America/New_York")).toInstant();
```

```text
=== 2) the five-hours-out bug, measured ===
the text both servers read:  2026-07-14T19:30
read by a Kolkata server:    2026-07-14T14:00:00Z
read by a New York server:   2026-07-14T23:30:00Z
same text, moments apart by: 9h 30m
```

### Walkthrough

1. **Three types, three honest wire shapes.** The `Instant` ends in `Z` — ISO-8601 saying
   "UTC, no ambiguity". The `LocalDateTime` looks almost identical but the `Z` is
   *missing* — that absence is the problem: an unanswered "where?" travelling as data.
2. **Capture 2 is the bug with a number on it.** One text, two readers, two assumptions —
   the "same" show at `14:00Z` *and* `23:30Z`. Neither server erred by its own lights;
   the *text* was underspecified. This is how the five-hours-out production bug happens.
3. **Why UTC as storage:** servers move between cloud regions, teams are global, daylight
   saving shifts wall clocks twice a year. `Instant`-in-UTC makes stored values immune to
   all of it; the human-facing conversion happens once, at the display edge, where the
   user's timezone is known.

### Where is it used?

Every moment on the platform — on-sale start, hold expiry, payment completion — is an
`Instant`: an on-sale at `2026-07-14T10:00:00Z` starts simultaneously for a buyer in
Kolkata and one in New York, which is what "on-sale time" must mean. Display conversion
belongs to the frontend.

### Common mistakes

- **`LocalDateTime` for a moment.** Compiles fine, works on one-zone laptops, breaks in
  production. If it names a moment that *happens*, it is an `Instant`.
- **Storing local times because "our users are all in one country".** Daylight saving
  breaks this twice a year; the business expands.
- **Converting to the user's zone on the server.** Stored values stay UTC; "7:30pm IST"
  in shared data bakes one user's zone into everyone's truth.
- **Hand-formatting dates** (`"14/07/2026 19:30"`). Reopens every ambiguity ISO-8601
  closes — day/month versus month/day classically.
- **Nuance:** a *future* event planned as "19:30 at the Leeds venue" is genuinely
  wall-clock-plus-place (if zone rules change, the promise is "19:30 local") — real
  systems store wall time plus zone id for that case. Moments that *happen* are
  `Instant`s, always.

### Summary

- `Instant` = pinned moment (UTC, `Z`); `LocalDateTime` = unpinned wall time (ambiguous);
  `LocalDate` = date only.
- Same unpinned text, two zones: 9h30m apart — measured.
- Store UTC, convert at the display edge, never in shared data.

### Practice questions

**Question 1.** A ticketing API stores a hold's expiry as `LocalDateTime` and saves the
text `2026-07-14T19:30:00`. The API server that *wrote* it runs in Kolkata (UTC+5:30);
the background job that *deletes expired holds* runs on a server in UTC. Using the
measured demo above as a guide, explain what goes wrong with this hold's lifetime, say
whether holds live too long or get deleted too early for this setup, and name the
one-type fix.

**Answer.** The writer meant "19:30 Kolkata time", which is `14:00Z` — that is the moment
the hold should die. The UTC deleter reads the unpinned text and assumes its own zone:
"19:30 *UTC*" — five and a half hours *later* than the writer intended. So holds live 5½
hours too long: seats stay locked all evening after their holds should have expired,
which on an on-sale day means inventory frozen while real buyers are turned away. (Had
the zones been reversed, holds would die early instead — the direction depends on which
side assumes what; the wrongness does not.) The fix is one type: store the expiry as an
`Instant` — the writer saves `2026-07-14T14:00:00Z`, the `Z` travels with the value, and
both services, whatever their zones, agree on the moment.

**Question 2.** A frontend developer complains: "the API sends `2026-07-14T19:30:00Z` but
the user is in India — the server should send `2026-07-15T01:00:00` so I can show it
directly." Explain why the server sending India-local times is the wrong fix, what the
correct division of labour is between server and frontend, and what would break for a
user opening the same page from New York.

**Answer.** Sending India-local text bakes one user's timezone into data served to
everyone — and strips the `Z`, so the payload becomes exactly the ambiguous unpinned text
this lesson measured going wrong. The server cannot know each viewer's zone reliably, and
the same cached/shared response goes to all of them. The correct division: the server
stores and sends the pinned UTC `Instant` (`...19:30:00Z`); the frontend — which runs on
the user's device and *knows* the device's timezone — converts for display. Every
mainstream frontend toolkit does this in one call. If the server instead sent
`2026-07-15T01:00:00` (India's wall clock), a New York user's browser would either show
that text raw — a time nine and a half hours wrong for them — or, worse, interpret the
unpinned text as New York time, shifting the moment again. One stored truth in UTC,
many correct local renderings at the edge.

---

## Lesson 41 — Money: the bug on your own screen, and BigDecimal

### What is it?

The rule the task calls the thing that actually matters: **money must never be a
`double`.** A `double` stores numbers as **floating point** — fast, and unable to
represent many decimal fractions exactly. **`BigDecimal`** stores decimal digits exactly
— slower, and correct. The only acceptable type for money.

### Why do we need it?

"Why is the total 10.299999999999999" is a *money bug affecting real people holding real
tickets*. Slivers round into visible wrongness on receipts, fail equality checks in
payment reconciliation, and compound across thousands of orders — and it happens for the
most ordinary amounts imaginable, as the demo shows with 10 pence.

### How does it work?

A `double` stores numbers as *binary* fractions — sums of halves, quarters, eighths. Some
decimals fit (`0.5`, `0.25`); `0.1` does not — just as 1/3 has no finite decimal form,
1/10 has no finite *binary* form. The `double` holds the nearest representable value, a
hair off, and arithmetic exposes the error. `BigDecimal` stores the decimal digits
themselves, so decimal arithmetic is exact. Two usage rules: **build from a `String`**
(`new BigDecimal("0.1")`), never from a double — the double's error would be imported;
compare with `compareTo`, not `==`/`equals`.

### The demo

Plain Java, compiled and run:

```text
0.1 + 0.2              = 0.30000000000000004
0.1 + 0.2 == 0.3       ? false
29.99 * 3              = 89.97
1.00 - 0.42            = 0.5800000000000001

BigDecimal 0.1 + 0.2   = 0.3
BigDecimal 29.99 * 3   = 89.97
BigDecimal 1.00 - 0.42 = 0.58

new BigDecimal(0.1)    = 0.1000000000000000055511151231257827021181583404541015625
new BigDecimal("0.1")  = 0.1
```

The endpoint — ten 10-pence booking fees, totalled both ways:

```java
record PriceDemo(double wrongTotal, BigDecimal rightTotal) {}

@GetMapping("/price-demo")
PriceDemo priceDemo() {
    double wrong = 0;
    for (int i = 0; i < 10; i++) {
        wrong += 0.10;                               // ten 10p fees, as double
    }
    BigDecimal right = BigDecimal.ZERO;
    for (int i = 0; i < 10; i++) {
        right = right.add(new BigDecimal("0.10"));   // the same, exactly
    }
    return new PriceDemo(wrong, right);
}
```

```text
=== the same wrongness, visible on the wire ===
{"wrongTotal":0.9999999999999999,"rightTotal":1.00}
```

### Walkthrough

1. **`0.1 + 0.2 = 0.30000000000000004`** — the task's promised sight, from real
   execution. The line below is the operational killer: `0.1 + 0.2 == 0.3` is **false**;
   code comparing computed money with `==` takes the wrong branch.
2. **`1.00 - 0.42 = 0.5800000000000001`** — change for a pound, wrong. Note `29.99 * 3`
   *happened* to print cleanly: floating-point errors are inconsistent, which makes them
   worse — lucky numbers pass tests, unlucky ones fail in production.
3. **Ten 10p fees totalled `0.9999999999999999`** on the wire. Display code showing two
   decimals masks it while the stored value stays wrong — until reconciliation compares
   exact values or thousands of slivers accumulate.
4. **The `BigDecimal` column is boringly correct** — and the last two lines show the
   constructor trap: `new BigDecimal(0.1)` preserves the double's error in 55-digit
   glory; `new BigDecimal("0.1")` is exactly one tenth. The error enters at the double's
   birth; keep doubles away from the value entirely.

(Many real systems alternatively store money as an integer count of the smallest unit —
pence, cents — also exact. `BigDecimal` is the general tool and this project's standard.)

### Where is it used?

Every money field: prices, fees, refunds, totals — `BigDecimal` in Java, a plain JSON
number on the wire. The rule reaches the database (Phase 1): column type
`DECIMAL`/`NUMERIC`, never `FLOAT`/`DOUBLE`.

### Common mistakes

- **`double price` anywhere money lives.** Works in demos, passes lucky-number tests,
  leaks slivers forever.
- **`new BigDecimal(0.1)`.** The double's error, imported. Always the `String`
  constructor.
- **Comparing with `==` or `equals`.** `==` on doubles fails as shown; `BigDecimal.equals`
  counts scale (`1.0` ≠ `1.00` by `equals`). Money comparison is `compareTo() == 0`.
- **Fixing the display, not the value.** Rounding for the receipt treats the symptom; the
  stored value still drifts and accumulates.
- **Division without instructions.** `BigDecimal` division can be infinite (1/3); say how
  to round: `divide(three, 2, RoundingMode.HALF_UP)`.

### Summary

- Doubles store binary fractions; most decimal amounts do not fit; errors are
  inconsistent and accumulate.
- `BigDecimal` from `String`s, compared with `compareTo`: exact.
- The wire showed it: `"wrongTotal":0.9999999999999999,"rightTotal":1.00`.

### Practice questions

**Question 1.** A ticketing checkout computes a 2.5% booking fee on a £40.00 ticket using
doubles, then checks `if (computedTotal == 41.00)` before sending the charge to the
payment provider. Explain the two separate ways this code can go wrong (one about
storage, one about comparison), and rewrite the calculation in two lines of correct
`BigDecimal` code — including the constructor choice and the comparison method.

**Answer.** Storage: the doubles holding 40.00, 0.025, and every intermediate result
store binary approximations — the computed total can come out as 40.99999999999999 or
41.000000000000004 depending on the amounts involved, and which amounts drift is
unpredictable (some, like 29.99 × 3, happen to print cleanly — luck, not safety).
Comparison: `==` demands bit-for-bit equality, so a hair-off total silently fails the
check and the charge logic takes the wrong branch — no exception, no log, just a skipped
or mishandled payment. The correct version:

```java
BigDecimal total = new BigDecimal("40.00").multiply(new BigDecimal("1.025"));
if (total.compareTo(new BigDecimal("41.00")) == 0) { /* send the charge */ }
```

String constructors keep every value exact from birth, and `compareTo() == 0` compares
numeric value while ignoring scale (41.00000 equals 41.00 by `compareTo`, which is the
comparison money wants — `equals` would say they differ).

**Question 2.** A teammate "fixes" a drifting order total by changing the
response-building code to round the double to two decimal places just before serialising
it to JSON. The receipts now look right. Explain what is still wrong, and give a concrete
scenario involving a refund of one order's exact total where the bug resurfaces despite
the pretty receipts.

**Answer.** Only the display was fixed: the stored total — the value used by every other
piece of code — is still the drifted double. The wrongness did not go away; it went
underground. Scenario: an order's internal total is `0.9999999999999999`; the receipt
rounds it to £1.00 and the payment provider was asked to charge £1.00 (the rounded
figure). The customer cancels, and the refund code refunds "the order's exact total" —
the stored `0.9999999999999999`. Depending on downstream rounding, the refund issued is
£0.99 or a value that simply does not equal the £1.00 charged; reconciliation now shows a
charge and a refund that differ, a 1p discrepancy flagged on a real customer's money —
multiplied across every refunded order. The value itself must be exact (`BigDecimal` from
strings throughout); rounding at the edge is for *formatting* an already-correct number,
never for repairing a wrong one.

---

## Lesson 42 — Mass assignment, and turning Jackson's silence into a loud no

### What is it?

Two dangers in one Jackson default. **Mass assignment**: a client sending fields it
should not be allowed to set — such as `"role":"ADMIN"` — and the server binding them,
because it accepted everything in the body. The quieter cousin: Jackson *silently
ignoring* unknown fields, hiding client mistakes — a typo'd field name quietly does
nothing. Countermeasures: DTOs that only contain client-settable fields, and
**`FAIL_ON_UNKNOWN_PROPERTIES`**, which turns unknown fields into an immediate `400`.

### Why do we need it?

Deserialisation is an open door: whatever JSON arrives, Jackson pours into the object. If
the object has a `role` field, a client can send one — nothing stops a request body.
Mass assignment is a named vulnerability class (sign-ups arriving as administrators,
profile edits setting balances). The silence hurts honest clients too: a misspelled field
vanishes without trace and the request "succeeds" with a hole in it.

### How does it work?

Three layers:

1. **Naive:** bind the request straight into an object with privileged fields. Whatever
   the client sends, wins.
2. **DTO:** the request binds into a record that *only has* client-settable fields. A
   smuggled `"role"` matches nothing — structurally dead. But under the default it dies
   *silently*, and so does an honest typo.
3. **Strict:** one line in `application.properties` —
   `spring.jackson.deserialization.fail-on-unknown-properties=true` — and any unmatched
   field fails deserialisation: `400` before the handler runs, naming the field.

### The demo

```java
record NaiveUser(String name, String role) {}    // role should NOT be client-settable

@PostMapping("/naive-signup")
NaiveUser naiveSignup(@RequestBody NaiveUser u) {
    return new NaiveUser(u.name(),               // binds whatever the client sent,
        u.role() != null ? u.role() : "USER");   // including a role
}

record SignUp(String name) {}                    // DTO: only what clients MAY set

@PostMapping("/signup")
NaiveUser signup(@RequestBody SignUp s) {
    return new NaiveUser(s.name(), "USER");      // role decided by the server
}
```

```text
=== PHASE A: Jackson defaults ===
--- 1) the naive endpoint binds everything: the attack succeeds ---
POST /naive-signup  {"name":"eve","role":"ADMIN"}
{"name":"eve","role":"ADMIN"}

--- 2) the DTO endpoint: role is not a field, attack blocked (silently) ---
POST /signup  {"name":"eve","role":"ADMIN"}
{"name":"eve","role":"USER"}

--- 3) but the same silence swallows an honest typo ---
POST /signup  {"nmae":"carol"}
HTTP/1.1 200 
{"name":null,"role":"USER"}

=== PHASE B: FAIL_ON_UNKNOWN_PROPERTIES turned on ===
--- 4) the ADMIN smuggle is now rejected loudly ---
HTTP/1.1 400 

--- 5) the typo is caught the first time it is tried ---
HTTP/1.1 400 
Unrecognized field "nmae" (class com.example.toy.ToyApp$SignUp), not marked as i...

--- 6) a correct request still works ---
{"name":"carol","role":"USER"}
```

### Walkthrough

1. **Capture 1 is the vulnerability, working.** One extra field in a sign-up body and eve
   is an administrator. *The record's shape is the attack surface* — binding requests
   into privileged objects is never acceptable.
2. **Capture 2 is the structural fix.** `SignUp` has one field; `"role":"ADMIN"` has
   nowhere to land. The deepest reason DTOs exist, beyond contract freedom.
3. **Capture 3 is the silence's cost.** Honest carol typo'd `nmae` — `200`, a nameless
   user, no error anywhere; she finds out days later as corrupt data.
4. **Captures 4–6: one property line.** Smuggle and typo both become `400` before the
   handler, with the log naming the culprit. Correct requests untouched. Trade-off stated
   honestly: strict mode also rejects *benign* extra fields (newer client, older server) —
   plan rollouts server-first. For an API where bodies should match the contract exactly,
   catching typos immediately beats silence.

### Where is it used?

Every endpoint accepting a body. The pattern composes: the DTO defines what clients may
send; its `@JsonProperty` names are the contract; value validation happens in the handler
(`422`); strict mode polices field *names* at the door (`400`). Mass assignment sits in
the OWASP API Security Top 10.

### Common mistakes

- **Binding requests into domain entities.** Every entity field — `role`, `id`,
  `balance` — becomes client-writable. Bind into DTOs; copy allowed fields over.
- **Trusting "my frontend never sends role".** curl exists. The server must be safe
  against arbitrary bodies.
- **Relying on the silent drop as security.** Silence that saves you also blinds you.
  Structure plus loudness covers both.
- **Strict mode without an evolution plan.** New request fields need server-first
  deployment, or versioning.

### Summary

- Mass assignment: the client sets what it should not, because the bound object had the
  field. DTOs kill it structurally.
- The silent default hides attacks *and* typos; `fail-on-unknown-properties=true` makes
  both loud `400`s before the handler runs.
- Entities never bind requests; DTOs do.

### Practice questions

**Question 1.** An API binds `PATCH /users/me` request bodies directly into its `User`
entity: `record User(String name, String email, String role, BigDecimal accountCredit) {}`.
A penetration tester sends `{"name":"x","accountCredit":"9999.00"}` and their account
balance changes. Explain why this worked, design the DTO that makes the attack
structurally impossible for this endpoint (users may edit only their name and email), and
say what the server's handler does with the two allowed fields.

**Answer.** It worked because the request was poured straight into the `User` entity:
`accountCredit` is a field of that record, so Jackson bound the smuggled value and the
handler saved the whole object — the entity's shape handed the client write access to
every column, including the privileged ones. The fix is a DTO holding exactly what a user
may edit: `record UpdateProfile(String name, String email) {}`. Now `accountCredit` (and
`role`) match nothing in the bound type — there is nowhere for the attack to land, no
matter what the body says. The handler's job with the two allowed fields: load the
*current* user from storage server-side, validate the incoming values (non-blank name,
plausible email — `422` otherwise), copy `name` and `email` from the DTO onto the stored
user, and save — `role` and `accountCredit` never pass through the request at all; they
remain whatever the server's own logic last set.

**Question 2.** With `FAIL_ON_UNKNOWN_PROPERTIES` enabled, a mobile-app release starts
sending a new optional field `"referral_code"` in sign-up requests before the server team
has added it to the `SignUp` DTO. Describe what every sign-up from the new app version
receives, why this is the designed behaviour rather than a bug, and what deployment order
would have avoided the breakage.

**Answer.** Every sign-up from the new app version is rejected with `400 Bad Request`
before the handler runs: `referral_code` matches no field of the `SignUp` DTO, and strict
mode treats an unmatched field as a malformed request — so new-version users cannot sign
up at all until something changes. It is designed behaviour because the server cannot
tell a *legitimate future field* from a *typo'd or smuggled one*: `referral_code`,
`nmae`, and `role` all look identical from where Jackson stands — unknown names — and the
whole point of strict mode is that unknown names fail fast instead of silently doing
nothing. The breakage was a sequencing error: request-body fields must be added
**server-first** — deploy a server whose DTO includes the optional `referral_code`
(ignoring or using it), *then* release the app that sends it. Old apps omit the optional
field and still work; new apps find a server that recognises it.

## Lesson 43 — Wrong types in the body: what Jackson rejects, and what it quietly bends

### What is it?

The third way a request body goes wrong. Broken *syntax* (`{"name": }` — not JSON at all)
and wrong *names* (unknown fields) are covered; this is wrong *types*: the body is valid
JSON, the field names all match — but a value has the wrong kind, like `"quantity":"three"`
where an `int` is expected. Jackson's behaviour splits in two: values it *cannot* convert
fail with `400` and a precise error naming the field and value; values it *can plausibly
bend* — a number wrapped in quotes, a decimal where an integer belongs — it converts
**silently**, by default.

### Why do we need it?

Type mismatches are everyday traffic: HTML form values arrive as strings (`"2"` instead
of `2`), JavaScript's one number type happily produces `2.5` where Java expects an `int`,
and confused clients send `"abc"` as a price. You must know which of these your server
refuses and which it bends — because the bends are invisible: nothing fails, nothing
logs, and the stored data is not quite what the client said. A quantity of 2.5 becoming 2
is half a ticket, gone without a sound.

### How does it work?

When Jackson reaches a field, it has the JSON value and the Java target type. Three
outcomes:

1. **Exact match** — JSON number into `int` or `BigDecimal`, string into `String`: binds
   cleanly.
2. **Convertible mismatch** — Jackson's default *coercions* kick in: a string containing
   a number (`"2"`, `"19.99"`) is parsed into the number type; a floating-point value
   into an `int` field is **truncated** (`2.5` → `2`). Succeeds silently.
3. **Unconvertible mismatch** — `"three"` into `int`, `"abc"` into `BigDecimal`:
   deserialisation fails, the handler never runs, the client gets `400`, the log names
   field, value, and expected type.

The failure happens at the same door as broken syntax and (in strict mode) unknown names
— before any handler code. One `400`, three possible causes: unparseable body, unknown
field, unconvertible value.

### The demo: five bodies against one record

```java
record TicketOrder(String event, int quantity, BigDecimal price) {}

@PostMapping("/ticket-orders")
TicketOrder ticketOrder(@RequestBody TicketOrder t) {
    return t;                                    // echo what Jackson built
}
```

```text
--- 1) correct types ---
POST {"event":"gig","quantity":2,"price":19.99}
{"event":"gig","quantity":2,"price":19.99}

--- 2) quantity as a word ---
POST {"event":"gig","quantity":"three","price":19.99}
HTTP/1.1 400
Cannot deserialize value of type `int` from String "three": not a valid `int` value

--- 3) price as gibberish ---
POST {"event":"gig","quantity":2,"price":"abc"}
HTTP/1.1 400
Cannot deserialize value of type `java.math.BigDecimal` from String "abc":
not a valid representation

--- 4) quantity as a decimal: ACCEPTED, and changed ---
POST {"event":"gig","quantity":2.5,"price":19.99}
{"event":"gig","quantity":2,"price":19.99}       <- 2.5 became 2, silently

--- 5) numbers wrapped in quotes: also quietly accepted ---
POST {"event":"gig","quantity":"2","price":"19.99"}
{"event":"gig","quantity":2,"price":19.99}
```

| Capture | Verdict | Why |
|---|---|---|
| `"three"` into `int` | `400`, handler never runs | no plausible conversion |
| `"abc"` into `BigDecimal` | `400`, handler never runs | no plausible conversion |
| `2.5` into `int` | `200` — **truncated to 2** | default coercion: float-to-int |
| `"2"`, `"19.99"` into numbers | `200` — parsed | default coercion: string-to-number |

Captures 2 and 3 are good errors: `400` before the handler, log naming the type, the
field's expected kind, the offending value. Capture 4 is the genuine surprise, found only
by running it: 2.5 tickets became 2 tickets, `200`, no trace — the client asked for
something impossible and the server silently granted something else. Capture 5 is the
other lenient default — usually welcome (HTML forms produce exactly this shape), but
still the server accepting a type the contract did not advertise. Both coercions are
switchable via Jackson settings for teams that want body types exact; the point is
knowing that *defaults bend*, so you test what your door lets through.

### The ladder, completed

These are `400`s (the body could not become an object at all), not `422`s (an object was
built, but its values broke your rules — like a *negative* quantity, which is
type-correct and reaches your handler). Three gates, three families of refusal: Jackson
polices *parseability and types* (`400`), the handler polices *meaning* (`422`), state
polices *timing* (`409`).

### Common mistakes

- **Assuming type checks catch everything numeric.** `2.5` sailed into an `int`. If
  fractional input must be an error, validate it — or turn the coercion off.
- **Returning the raw Jackson message to clients in production.** It names Java classes
  (`java.math.BigDecimal`) — internals that do not belong on the wire. Log the full
  message; send the client a clean "quantity must be a whole number".
- **Catching type errors in the handler.** They happen before the handler; there is
  nothing to catch there. Door-level errors are configured, not coded around.
- **Conflating `400` with `422` in client guidance.** `"three"` tickets is a `400` (not
  an `int`); `-3` tickets is a `422` (an `int` your rules reject). Clients fix the first
  by fixing types, the second by fixing values.

### Summary

- Valid JSON + matching names + wrong value kind = the third body failure. Unconvertible
  values die at the door with `400` and a precise log line; convertible ones bend
  silently.
- Two default coercions to know: quoted numbers parse (`"2"` → `2`), floats truncate into
  ints (`2.5` → `2`). The truncation can silently rewrite client data.
- `400` = could not become an object (syntax, unknown name, bad type). `422` = became an
  object, values broke the rules. Different gates, different client fixes.

### Practice questions

**Question 1.** An endpoint binds `record Booking(String event, int seats, BigDecimal
total) {}`. Three requests arrive: (a) `{"event":"gig","seats":"four","total":50.00}`;
(b) `{"event":"gig","seats":3.7,"total":50.00}`; (c) `{"event":"gig","seats":-2,
"total":50.00}`. For each, say what the client receives under Jackson's defaults, whether
the handler runs, and — where the handler does run — what value it sees and what it
should do.

**Answer.** (a) `400 Bad Request`; the handler never runs. `"four"` is a string with no
numeric reading, so Jackson cannot build the object — the log shows `Cannot deserialize
value of type `int` from String "four"`. The client fixes it by sending a JSON number.
(b) `200` unless the handler intervenes — and this is the trap: Jackson's default
float-to-int coercion *truncates* `3.7` to `3`, the handler runs and sees `seats == 3`,
and nothing anywhere records that the client asked for 3.7. If fractional seat counts
must be errors, the handler cannot catch it (it only ever sees `3`); the coercion itself
must be disabled, or the field typed to reject fractions. (c) The handler **runs** —
`-2` is a perfectly valid `int`, so Jackson binds it happily. The handler sees
`seats == -2`, and value-checking is its job: `-2` breaks the business rule "seats must
be positive", so it returns `422 Unprocessable Entity` with a message like "seats must be
at least 1". Type gate, then meaning gate.

**Question 2.** A production API logs this during an incident: `Cannot deserialize value
of type 'java.time.Instant' from String "14/07/2026 19:30"`. The client team insists
their request is "obviously a valid date". Explain to them, in plain terms, which gate
their request died at, why their date is invalid *to this gate* despite being readable by
a human, what exact format the field expects, and why the fix belongs on their side of
the wire.

**Answer.** The request died at the deserialisation gate — the point where the JSON text
is turned into a Java object, before any application code runs. The server never saw the
booking; it saw a body it could not convert. The date is human-readable but not
machine-expected: an `Instant` field is parsed strictly as ISO-8601 — the format
`2026-07-14T19:30:00Z` — year first, `T` between date and time, and a timezone marker
(`Z` for UTC). `"14/07/2026 19:30"` fails on all three counts: day-first order, a space
instead of `T`, and no timezone at all — and that last one is not pedantry, since
"19:30" in Mumbai and "19:30" in New York are different moments, so a timestamp without
a zone is genuinely ambiguous, not merely misformatted. The fix belongs on the client's
side because the field's format is part of the published contract, exactly like its
name: the server advertises an ISO-8601 instant, and every client must send that shape.
If the server instead added a lenient parser for this one client's habit, it would be
guessing at ambiguity (is `04/07` April or July?) and would fork the contract — every
other correct client now depends on a server that accepts two formats. Contract formats
are frozen promises; clients conform to them.

## Lesson 44 — Null versus absent: the three things a client can mean, and why PATCH is genuinely hard

### What is it?

For any one field, a JSON body can say **three** different things: the field is **present
with a value** (`"nickname":"PK"` — *set it*), **present but null** (`"nickname":null` —
*clear it*), or **absent entirely** (no `nickname` key — *don't touch it*). JSON can
express all three. A Java field cannot: after binding, both "present but null" and
"absent" land in the object as the same `null`. The distinction the client carefully
expressed dies at the door — and `PATCH`, the method whose whole job is "update only what
I mention", is exactly where that loss hurts.

### Why do we need it?

Partial updates are everywhere — profile edits, settings toggles, an admin tweaking one
field — and `PATCH` is their method. `PUT` is simple: it replaces the whole resource, so
anything left out is cleared, no ambiguity. `PATCH` promises something more delicate:
*change what I name, leave the rest alone*. That promise needs all three meanings. If the
server cannot tell "clear it" from "don't touch it", one of those intents becomes
silently impossible — usually clearing: users can set a nickname but never remove it, and
nobody gets an error saying so.

### How does it work?

Bind a PATCH body into an ordinary record and the collapse is mechanical: Jackson sets
fields it finds and leaves the rest at Java's default — `null` for object types:

| Client sends | Client means | Your Java field |
|---|---|---|
| `"nickname":"PK"` | set it to PK | `"PK"` |
| `"nickname":null` | clear it | `null` |
| *(no nickname key)* | don't touch it | `null` — **identical** |

The standard idiom `if (p.nickname() != null) update it` therefore implements only two
meanings: set, and everything-else-is-don't-touch. "Clear" is unreachable. To keep all
three, the handler must know whether the *key was present*, not just what its value was —
and a `Map` remembers the difference between a missing key and a key whose value is null.

### The demo: naive PATCH vs presence-aware PATCH

```java
record ProfilePatch(String name, String nickname) {}

@PatchMapping("/profile")                       // the naive PATCH
Map<String, String> patchProfile(@RequestBody ProfilePatch p) {
    if (p.name() != null) profileName = p.name();
    if (p.nickname() != null) profileNickname = p.nickname();
    return profile();
}

@PatchMapping("/profile-v2")                    // presence-aware PATCH
Map<String, String> patchProfileV2(@RequestBody Map<String, Object> body) {
    if (body.containsKey("name")) profileName = (String) body.get("name");
    if (body.containsKey("nickname")) profileNickname = (String) body.get("nickname");
    return profile();
}
```

```text
--- 1) starting state ---
{"name":"Pavan","nickname":"PK"}

--- 2) naive PATCH: change name only (nickname absent = do not touch) ---
PATCH /profile  {"name":"Pavan Kumar"}
{"name":"Pavan Kumar","nickname":"PK"}           <- correct: nickname untouched

--- 3) naive PATCH: client wants to CLEAR the nickname ---
PATCH /profile  {"nickname":null}
{"name":"Pavan Kumar","nickname":"PK"}           <- BUG: still PK, 200 OK, no error

--- 4) presence-aware PATCH: clear the nickname ---
PATCH /profile-v2  {"nickname":null}
{"name":"Pavan Kumar","nickname":null}           <- cleared

--- 5) presence-aware PATCH: name changes, absent nickname stays cleared ---
PATCH /profile-v2  {"name":"PK Yadav"}
{"name":"PK Yadav","nickname":null}
```

Capture 2 is why the naive version survives code review: the common case works, so the
bug ships. Capture 3 is the bug, and its shape matters — a **200**. The client expressed
"clear my nickname" in legal JSON; Jackson bound `null`; the `!= null` guard read it as
"absent"; the handler skipped it and reported success. Silent-success bugs are far harder
to find than any `400`. Captures 4–5 show the repaired contract: `containsKey` is true
for key-present-value-null (clear) and false for key-absent (keep). The map fix has real
costs — typed fields are gone, so type checking and unknown-name checking become the
handler's manual job; production codebases often use a dedicated wrapper type that
records "was this field present?". The null-means-delete convention is standardised as
**JSON Merge Patch** (RFC 7386): send fields to change, `null` to delete, omit to keep.
And the honest alternative: if field-level updates aren't needed, expose only `PUT` —
full replacement has one meaning per field, absent means gone.

### Common mistakes

- **`if (field != null)` as the entire PATCH implementation.** Makes "clear this field"
  unexpressible, while every test that only sets fields passes green.
- **Treating `PATCH` as "PUT but smaller".** `PUT` replaces (absent = clear); `PATCH`
  merges (absent = keep). A partial body to a true `PUT` endpoint wipes every omitted
  field.
- **Clearing on null without documenting it.** If `"nickname":null` deletes, the contract
  must say so — a client sending explicit nulls out of tidiness will delete data it meant
  to keep.
- **Forgetting the map fix trades away type safety.** Presence is kept, Jackson's type
  policing is lost; the handler must validate kinds and names itself.

### Summary

- One JSON field, three meanings: value = set, null = clear, absent = keep. Java binding
  collapses the last two into one `null`.
- The naive `!= null` PATCH silently ignores clear requests and returns `200` — a lying
  success, invisible in logs.
- Preserve key-presence across the binding (`Map` + `containsKey`, or a presence-aware
  wrapper type). The convention has a name: JSON Merge Patch (RFC 7386).
- No partial updates needed? Use `PUT` and the problem vanishes with the flexibility.

### Practice questions

**Question 1.** A teammate's `PATCH /events/{id}` handler binds into
`record EventPatch(String title, String description, String imageUrl) {}` and updates
each stored field when the bound value is non-null. A user reports: "I removed my event's
image in the app, the app said saved, but the image is still there." Trace the request
from the app to the stored data, naming exactly where the user's intent was lost, why the
app saw a success, and two different ways the server could be fixed — stating one cost of
each.

**Answer.** The app expressed "remove the image" the standard merge-patch way:
`PATCH /events/42` with body `{"imageUrl":null}`. The body is valid JSON with a known
field, so it passes the door and Jackson builds an `EventPatch` — and here the intent
dies: `imageUrl` is bound to `null`, which is bit-for-bit identical to what an *absent*
`imageUrl` would have produced. The handler's guard `if (p.imageUrl() != null)` reads
that `null` as "not mentioned", skips the update, finishes without error, and returns
`200` — so the app truthfully reported "saved" about a request that changed nothing. The
stored image was never touched. Fix one: make the handler presence-aware — bind the body
into a `Map<String, Object>` (or a wrapper type that records whether each key appeared)
and branch on `containsKey("imageUrl")`: present-with-null clears the image,
absent leaves it. Cost: binding to a map abandons typed fields, so the handler must
manually police types and unknown names that Jackson previously handled. Fix two: stop
expressing removal through PATCH at all — model the image as a sub-resource and expose
`DELETE /events/{id}/image`, which says "remove" unambiguously with no null needed. Cost:
a wider API surface, and a client combining "change title and remove image" now needs two
requests instead of one.

**Question 2.** Your API's docs must explain `PATCH /profile` to client developers in one
short table: what happens for each of the three ways the `nickname` field can appear in
the body, plus one warning for developers whose HTTP libraries "helpfully" serialise
every object field, including unset ones, as explicit `null`s. Write that table and the
warning.

**Answer.**

| `nickname` in your request body | Meaning | Server action |
|---|---|---|
| `"nickname": "PK"` | set | nickname becomes `"PK"` |
| `"nickname": null` | clear | stored nickname is deleted |
| key not present | keep | stored nickname is left unchanged |

**Warning.** This endpoint treats an explicit `null` as a deletion order, not as "no
opinion". If your HTTP library serialises every field of your request object — including
the ones you never assigned — it will send `"nickname":null`, `"name":null`, … for
everything you didn't set, and a request you meant as "change my name" will also **erase
your nickname and every other unset field**. Configure the library to omit unassigned
fields from the JSON it sends (most have an "ignore/exclude nulls" or "include only
set values" option) before pointing it at any PATCH endpoint with these semantics.

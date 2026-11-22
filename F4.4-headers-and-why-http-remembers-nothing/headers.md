# Task F4.4 — Headers, and why HTTP remembers nothing

## Lesson 25 — Content-Type and Accept: agreeing on a format

### What is it?

Two headers that let the two sides of an HTTP exchange agree on a data format.
**`Content-Type`** is a label on a body, saying what the bytes *are* — whoever sends a
body sets it, so it appears on requests (a `POST` body) and on responses (the server's
answer). **`Accept`** is a request header stating what formats the client is *willing to
receive back*. The server picks a format from that list — or refuses with
`406 Not Acceptable`. If the client's body label is a format the server cannot read, the
server refuses with `415 Unsupported Media Type`. This negotiation dance has a name:
**content negotiation**.

Format names look like `application/json`, `text/html`, `image/png` — a type, a slash, a
subtype. The official term is **media type**.

### Why do we need it?

A body is just bytes. The sequence spelling `{"name":"gig"}` could be JSON, or plain text
that happens to look like JSON, or the first line of a CSV file. Nothing in the bytes
says which. The receiver needs to pick a parser *before* reading, and `Content-Type` is
what it picks by. Servers do not guess; they trust the label or refuse.

### How does it work?

Two directions, two headers:

- **Client sends a body** → it labels the body with `Content-Type`. The server checks:
  can I parse this format on this endpoint? No → `415`, often with an `Accept` header in
  the *response* listing what it can parse. The body is never even opened.
- **Client wants a body back** → it may send `Accept` listing formats it can handle. The
  server checks: can I produce any of these? No → `406`. Yes → it produces one and labels
  its own response with `Content-Type`.

No `Accept` header means "anything is fine". Most clients send `Accept: */*` by default —
which is why plain `curl` never shows a `406`.

### The demo

The toy app, unchanged, driven with different labels (all captures real):

```text
=== A) body labelled application/json -> accepted ===
curl -X POST -H 'Content-Type: application/json' -d '{"name":"gig"}' /events
HTTP/1.1 201 
Location: /events/0
Content-Type: application/json

=== B) the SAME bytes labelled text/plain -> 415 ===
curl -X POST -H 'Content-Type: text/plain' -d '{"name":"gig"}' /events
HTTP/1.1 415 
Accept: application/json, application/*+json        <- the server's hint
{"timestamp":"...","status":415,"error":"Unsupported Media Type","path":"/events"}

=== C) client will accept JSON -> 200, note the response Content-Type ===
curl -H 'Accept: application/json' /hello
HTTP/1.1 200 
Content-Type: application/json
{"message":"hello","id":1}

=== D) client insists on XML only -> 406 ===
curl -H 'Accept: application/xml' /hello
HTTP/1.1 406 
                                                    <- no body at all

=== E) no Content-Type at all on a POST with a body -> 415 ===
HTTP/1.1 415 
```

### Walkthrough

1. **A vs B is the whole `Content-Type` story.** Identical body bytes; only the label
   changed, and the outcome went from `201` to `415`. The server did not peek at the
   bytes; it trusted the label, saw a format `/events` cannot parse, and refused before
   opening the body. The handler never ran.
2. **The `415` response carries an `Accept` header of its own** — the server saying
   "here is what I *can* read; relabel and resend". A refusal with directions.
3. **C shows both headers working:** the request's `Accept` said "JSON is fine", the
   server obliged, and its response's `Content-Type: application/json` labels what it
   produced, so the client knows which parser to run.
4. **D is the refusal on the response side.** XML demanded, only JSON available, `406`.
   The body is completely empty — Spring normally sends its error JSON, but this client
   declared it only accepts XML, so the server could not even express the *error* in an
   acceptable format.
5. **E:** a body with no label at all is also `415` — the server will not guess.

### Where is it used?

Every frontend request with a body carries `Content-Type: application/json`; every API
response is labelled the same way (Spring sets it automatically). Classic `Accept` use:
one URL serving two audiences — a browser sending `Accept: text/html` gets a web page, a
program sending `Accept: application/json` gets data. And the `415` from a curl `POST`
missing `-H 'Content-Type: application/json'` is one of the most common beginner stumbles
in existence.

### Common mistakes

- **Forgetting `Content-Type` on a `POST` with a JSON body.** Instant `415`; handler
  never runs; the bytes being valid JSON is irrelevant.
- **Confusing `415` with `400`.** `400`: the body could not be parsed *as the claimed
  format*. `415`: the claimed format itself is unsupported — the body was never opened.
- **Confusing the sides.** `Content-Type` describes the body *you send*; `Accept`
  describes what you want *back*. A request can carry both.
- **Assuming `Accept` forces the server.** It cannot produce what it does not have — the
  honest answers are a format from the list, or `406`.

### Summary

- `Content-Type`: label on any body, both directions. `Accept`: client's wish-list for
  the response.
- Server cannot read your label → `415` (refused at the door, body unopened). Server
  cannot produce your wish → `406`.
- Same endpoint, different headers, different results — with no server-side state
  anywhere. That is content negotiation.

### Practice questions

**Question 1.** A developer runs
`curl -X POST -d '{"name":"Gig Night"}' http://localhost:8080/events` against a JSON API
and gets `415 Unsupported Media Type`. The JSON is flawless — they check it three times.
Explain why the server refused, why it never even looked at the flawless JSON, and give
the one change to the command that fixes it. (Hint: when curl sends `-d` data without
instructions, it labels the body `application/x-www-form-urlencoded` — an old web-form
format.)

**Answer.** curl silently labelled the body `application/x-www-form-urlencoded`, so the
server received flawless JSON bytes wearing a web-form label. The endpoint only parses
`application/json`; the server trusts labels rather than sniffing bytes, so it refused at
the door with `415` — the body was never opened, which is why its flawlessness never
mattered. The check happens *before* parsing, so no amount of fixing the JSON changes the
outcome. The fix is to label the body correctly:
`curl -X POST -H 'Content-Type: application/json' -d '{"name":"Gig Night"}' http://localhost:8080/events`.

**Question 2.** One endpoint, `GET /events/5`, gets two requests. Request one carries
`Accept: application/json`; the server answers `200` with
`Content-Type: application/json` and a JSON body. Request two carries
`Accept: image/png`; the server answers `406`. A teammate says "that endpoint is flaky —
same URL, different results". Using only what the two request headers say, explain why
the server's behaviour is consistent, and name the mechanism these two headers implement.

**Answer.** The two requests asked for different things, and each got the only honest
answer to what it asked. Request one said "I can handle JSON"; the server can produce
JSON, so it did, labelling the response so the client knows what arrived. Request two
said "I can handle *only* PNG images"; the server has no way to render an event as a PNG,
and inventing one or sending JSON anyway would violate the client's stated requirement —
so `406 Not Acceptable` is the consistent, correct reply. The behaviour is a pure
function of the request's headers; nothing was remembered between calls and nothing is
flaky. The mechanism is content negotiation: `Accept` states what the client will take,
`Content-Type` labels what the server produced, and `406` is the dance failing honestly.

---

## Lesson 26 — Conditional requests: Last-Modified, If-Modified-Since, and the free 304

### What is it?

A **conditional request** is a `GET` that says: "only send the body if it changed since
the copy I already have." A matched pair of headers: the server labels each response with
**`Last-Modified`** — a timestamp of when the resource last changed. The client stores
the body *and* the label, and next time sends the label back as **`If-Modified-Since`**.
If nothing changed, the server answers **`304 Not Modified`** — a status line with *no
body at all* — meaning "your copy is still good, keep using it."

### Why do we need it?

Most refreshes fetch something that has not changed. The on-sale scenario: thousands of
people on one event page hammering refresh; the details change maybe once an hour, the
requests come in thousands per minute. Without conditional requests, every refresh
re-downloads the identical body. With them, the common case costs a status line and
headers: near-zero bytes, near-zero work, and the client's screen is just as correct.

### How does it work?

1. Plain `GET` → `200`, full body, plus `Last-Modified: <timestamp>`.
2. Client keeps both the body and the timestamp.
3. Next time: `GET` with `If-Modified-Since: <that timestamp>`.
4. Server compares: changed *after* that timestamp? **No** → `304`, no body. **Yes** →
   fresh `200`, fresh body, fresh `Last-Modified`; the client replaces its stored pair.

Limitation to carry along: the timestamp has one-second precision, so two changes inside
the same second can be indistinguishable — the gap `ETag` exists to close.

### The demo

The venue endpoints maintain a change timestamp; `GET` compares, `PUT`/`PATCH` move the
label forward:

```java
private Venue venue = new Venue("Old Hall", "Leeds");
private Instant venueLastModified = Instant.now();   // when the venue last changed

@GetMapping("/venue")
ResponseEntity<Venue> getVenue(
        @RequestHeader(value = "If-Modified-Since", required = false) String ifModifiedSince) {
    if (ifModifiedSince != null) {
        Instant clientHas = Instant.from(
            DateTimeFormatter.RFC_1123_DATE_TIME.parse(ifModifiedSince));
        if (!venueLastModified.truncatedTo(ChronoUnit.SECONDS).isAfter(clientHas)) {
            return ResponseEntity.status(304).build();   // nothing newer; no body
        }
    }
    return ResponseEntity.ok()
        .lastModified(venueLastModified)                 // label this version
        .body(venue);
}
```

The live session:

```text
=== 1) plain GET: full body, plus a Last-Modified label ===
HTTP/1.1 200 
Last-Modified: Wed, 21 Oct 2026 19:56:18 GMT
{"name":"Old Hall","city":"Leeds"}

=== 2) conditional GET: send the label back ===
HTTP/1.1 304 
   downloaded bytes: 0 (vs 34 for the full GET)

=== 3) ten conditional refreshes, like a browser hammering F5 ===
304 304 304 304 304 304 304 304 304 304 

=== 4) someone changes the venue (PATCH city -> York) ===
{"name":"Old Hall","city":"York"}

=== 5) the SAME conditional GET now: fresh body, new label ===
HTTP/1.1 200 
Last-Modified: Wed, 21 Oct 2026 19:56:22 GMT
{"name":"Old Hall","city":"York"}
```

### Walkthrough

1. Step 1 is an ordinary `GET` with one gift: the `Last-Modified` header (that odd date
   format is HTTP's standard for header timestamps).
2. Step 2 sends the gift back; the answer shrinks to a bare `304` — **0 bytes**
   downloaded against 34 for the full body. On a megabyte page, the saving multiplies a
   millionfold.
3. Step 3 is the on-sale crowd in miniature: ten refreshes, ten `304`s, no bodies built,
   every client still correct.
4. Step 4 changes reality — and crucially the handler moves `venueLastModified` forward.
   A resource that changes without updating its label poisons every cache holding the
   old copy.
5. Step 5 is the *same* conditional request, but the server's timestamp (19:56:22) is now
   newer than the client's label (19:56:18): full `200`, York body, new label. The loop
   begins again.

No memory of *who* asked anywhere: the server never stored "this client has version X".
Everything travels inside each request — statelessness.

### Where is it used?

Browsers do this automatically for images, stylesheets, scripts — much of why a second
visit feels faster. APIs use it for anything polled repeatedly: event details and seat
maps are classic — thousands of identical reads between rare writes, the shape `304`
rewards.

### Common mistakes

- **Forgetting to update the timestamp on writes.** Clients with an old label get `304`
  forever — permanently stale data that looks healthy.
- **Expecting the `304` to have a body.** It never does; that is the point.
- **Thinking `304` is an error or redirect because it is `3xx`.** It is the happiest
  answer in the family: *you already have it*.
- **Trusting one-second precision too far.** Two writes in one second can leave a stale
  label looking current. Hence `ETag`.

### Summary

- `Last-Modified` labels the version; `If-Modified-Since` sends the label back; `304`
  means "your copy is still good" and carries no body.
- Writes must move the label forward, or caches go permanently stale.
- The server stores nothing per client — the label travels in every request.

### Practice questions

**Question 1.** A client fetches `GET /venue` and receives `200`, a body of
`{"name":"Old Hall","city":"Leeds"}`, and `Last-Modified: Wed, 21 Oct 2026 19:56:18 GMT`.
It refreshes twice: the first refresh sends
`If-Modified-Since: Wed, 21 Oct 2026 19:56:18 GMT` and gets `304`. Then someone else
updates the city. The second refresh sends the *same* `If-Modified-Since` header and gets
`200` with a new body and `Last-Modified: Wed, 21 Oct 2026 19:56:22 GMT`. Say what the
client should do with its stored copy and stored label after each of the three responses,
and what the next refresh should send.

**Answer.** After the first `200`: store the body and the label `19:56:18` — that pair is
the client's local copy. After the `304`: change nothing; the empty answer means "your
copy is still good", so the client keeps displaying the stored Leeds body and keeps the
same label for future requests. After the second `200`: replace both — store the new body
(with the updated city) and the new label `19:56:22`; the old pair is obsolete. The next
refresh sends `If-Modified-Since: Wed, 21 Oct 2026 19:56:22 GMT` — always the newest
label the client holds — and will receive `304`s until the venue changes again.

**Question 2.** A teammate implements `PATCH /venue` so it updates the venue but forgets
the line that moves the last-modified timestamp forward. The `GET` handler still compares
`If-Modified-Since` against the (now stale) timestamp. Describe what every client that
already holds the old label will see after the `PATCH`, why the bug is invisible on
dashboards (every response is a success code), and which single line fixes it.

**Answer.** The `PATCH` changes the venue's content but leaves the timestamp where it
was. Every client holding the old label asks "modified since 19:56:18?" and the server,
comparing against its unmoved timestamp, honestly-but-wrongly answers `304` — so every
one of those clients keeps displaying the *old* venue forever. New clients (no label yet)
get the new data, so the system shows different truths to different people. On
dashboards, nothing looks wrong: `304` and `200` are both success-family codes, the error
rate stays at zero, and no alert fires — stale data is not an error the infrastructure
can see. The fix is one line in the `PATCH` (and `PUT`) handler:
`venueLastModified = Instant.now();` — every write must move the label. (The deeper cure
is a label computed from the content itself, so forgetting is impossible — that is the
`ETag`.)

---

## Lesson 27 — ETag and If-None-Match: a version label with no clock in it

### What is it?

An **`ETag`** (entity tag) is a short string the server attaches to a response as a label
for *that exact version* of the resource — change the resource, change the label. The
client stores it and sends it back in **`If-None-Match`**, meaning: "only send a body if
the current version's label is *not* this one." Labels match → `304 Not Modified`, no
body. Labels differ → fresh `200`, fresh body, fresh label. Same problem as the timestamp
pair — skip re-sending unchanged bodies — but versions are identified by *content*, not
*time*.

### Why do we need it?

Timestamps have two cracks. Precision: `Last-Modified` counts in whole seconds, so two
changes inside one second are indistinguishable — a client can hold stale data while its
label looks current. Discipline: a timestamp must be *remembered to be moved* on every
write; one forgotten line and clients get `304` forever against changed content. An
`ETag` computed from content closes both: any change to the bytes changes the label — no
clock, no precision gap, nothing to forget.

### How does it work?

1. Server computes a label from the current content — typically a hash (a short
   fingerprint of the bytes) — and sends `ETag: "v-7d2a2ae9"`. The quotes are part of the
   format.
2. Client stores body + label.
3. Next request carries `If-None-Match: "v-7d2a2ae9"`.
4. Server recomputes the current label and compares strings. Equal → `304`, no body.
   Different → `200`, new body, new label.

The header's name explains the comparison: *if none* of the labels I hold *match* the
current version, send it. When a request carries both `If-None-Match` and
`If-Modified-Since`, the `ETag` check wins — the more precise instrument.

### The demo

The venue handler computes its label from content — `venue.hashCode()` is identical for
identical field values, different for different ones:

```java
@GetMapping("/venue")
ResponseEntity<Venue> getVenue(
        @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
        @RequestHeader(value = "If-Modified-Since", required = false) String ifModifiedSince) {
    String etag = "\"v-" + Integer.toHexString(venue.hashCode()) + "\"";  // label FROM content
    if (ifNoneMatch != null) {                           // ETag check wins when both sent
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304).eTag(etag).build();  // same version; no body
        }
    }
    // ... (the Last-Modified comparison still lives here) ...
    return ResponseEntity.ok()
        .eTag(etag)                                      // version label, no clock in it
        .lastModified(venueLastModified)
        .body(venue);
}
```

The live session:

```text
=== 1) plain GET: body plus an ETag label computed from the content ===
HTTP/1.1 200 
ETag: "v-7d2a2ae9"
{"name":"Old Hall","city":"Leeds"}

=== 2) conditional GET with If-None-Match: 304, zero bytes ===
HTTP/1.1 304 
ETag: "v-7d2a2ae9"
   downloaded bytes: 0

=== 3) PATCH the city to York ===
{"name":"Old Hall","city":"York"}

=== 4) same conditional GET: content changed, so the label no longer matches ===
HTTP/1.1 200 
ETag: "v-78f5e35d"
{"name":"Old Hall","city":"York"}

=== 5) PATCH the city BACK to Leeds ===
{"name":"Old Hall","city":"Leeds"}

=== 6) the ORIGINAL label works again: same content, same ETag ===
HTTP/1.1 304 
ETag: "v-7d2a2ae9"
```

### Walkthrough

1. The Leeds venue wears `"v-7d2a2ae9"` — no timestamp; the label *is* a fingerprint of
   the content.
2. Labels match → `304`, zero bytes. Same economy as the timestamp version, different
   engine.
3. Steps 3–4: the city becomes York and the recomputed label is `"v-78f5e35d"` — nobody
   *updated* anything; the label changed because the content did. The
   forgot-to-move-the-timestamp bug is structurally impossible: there is nothing to move.
4. Steps 5–6 prove what an ETag really is: the city reverts to Leeds and the *original*
   label comes back, so the client's old stored label matches again — `304`. A timestamp
   could never do this; time only moves forward. Identical content, identical label,
   whatever the clock says.

No per-client memory anywhere: the server recomputes and compares; the label travels in
every request.

### Where is it used?

Browsers and CDNs (the worldwide cache networks serving most static files) lean on
`ETag`s constantly. For the ticketing platform: seat maps are the perfect customer —
unpredictable, bursty changes where one-second precision genuinely fails during an
on-sale. Preview: the same label has a sibling, `If-Match`, used on *writes* — "only
apply my `PUT` if the version is still the one I edited", refused with
`412 Precondition Failed` otherwise. That cures the stale-client lost-update problem;
Phase 5 builds on it.

### Common mistakes

- **Labels that don't track content** — a constant (eternal `304` staleness) or a fresh
  random string per response (never a `304` at all). The label must be a pure function of
  the version.
- **Dropping the quotes.** The server sends `ETag: "v-7d2a2ae9"`; the client must return
  exactly that, quotes included. Strip them and nothing ever matches — the optimization
  silently dies into full downloads.
- **Expensive fingerprints.** If computing the hash loads the whole resource anyway, the
  bandwidth saving survives but the server-work saving shrinks. Real systems often store
  a version counter bumped on each write.
- **Confusing `If-None-Match` (reads) with `If-Match` (writes).** "Send it only if it
  changed" versus "change it only if it hasn't". Opposite conditions, same label.

### Summary

- `ETag` = version label computed from content; `If-None-Match` = "only send a body if my
  label is out of date"; match → `304`.
- No clock: no one-second gap, no forgot-to-update bug; reverted content revives old
  labels.
- `ETag` beats `Last-Modified` when both are present; `If-Match` (writes) is the sibling
  that prevents lost updates.

### Practice questions

**Question 1.** A seat-map endpoint serves `GET /events/5/seats`. At 20:00:00.100 a seat
is sold, and at 20:00:00.800 — the *same second* — another seat is sold. A client fetched
the map at 20:00:00.400, between the two sales. Explain why
`Last-Modified`/`If-Modified-Since` (whole-second timestamps) can leave this client
believing its copy is current when it is not, and why an `ETag` computed from the seat
map's content cannot make that mistake.

**Answer.** With timestamps, every moment inside that second truncates to the same label:
`20:00:00`. The client's copy (fetched at .400, after the first sale) carries the label
`20:00:00`; the second sale (.800) stamps the resource `20:00:00` as well. The client
asks "modified since 20:00:00?" and the server compares — .800 truncated is not *after*
20:00:00 — and answers `304`. The client keeps a map missing the second sale while
believing it is current; whole-second precision simply cannot see inside the second. An
`ETag` has no clock to fool: the client's label is a fingerprint of the one-seat-sold
map, the server's current label is a fingerprint of the two-seats-sold map, the strings
differ, and the server sends the full fresh map. Content comparison is immune to timing.

**Question 2.** A venue resource is `{"name":"Old Hall","city":"Leeds"}` with
`ETag: "v-7d2a2ae9"`. A client stores the body and the label. Then the city is changed to
York, and later changed back to Leeds. The client — which slept through both changes —
now sends `GET /venue` with `If-None-Match: "v-7d2a2ae9"`. Say what the server answers,
whether the client's stored copy is safe to keep using, and why a timestamp-based label
would have forced a pointless full download in the same situation.

**Answer.** The server recomputes the label from the *current* content — Leeds again —
and gets `"v-7d2a2ae9"`, exactly what the client sent, so it answers `304` with no body.
The stored copy is perfectly safe: a matching content-derived label means the bytes the
client holds are identical to the bytes the server would send; the detour through York is
invisible and irrelevant. A timestamp label, though, only moves forward: both the change
and the revert pushed `Last-Modified` later, so the client's old timestamp would look
stale, and the server would ship the full body — bytes identical to what the client
already holds. The ETag's content fingerprint recognises "same as before"; a clock can
only recognise "earlier than now".

---

## Lesson 28 — Cache-Control and Location: storage rules and addresses

### What is it?

Two headers that are the server leaving instructions for whoever holds its response.
**`Cache-Control`** states the storage rules — whether this response may be saved and
reused instead of re-asking, by whom, and for how long. **`Location`** states an address —
on a `201 Created` it is the URL of the thing just created; on a redirect (`301`/`302`)
it is where to go instead. Same header, two jobs, told apart by the status code next to
it.

### Why do we need it?

`Cache-Control`: reuse without rules is dangerous, and no reuse is wasteful. A venue's
details can safely be reused for a minute by anyone; an organiser's private data must
never be written to any disk between the server and that one user's screen. Only the
server knows which is which, so it must *say* so on every response — statelessness means
nothing between requests is remembered.

`Location`: "I created it" is half an answer. The server chose where the new thing lives
(its URL, often containing a generated id). Without `Location` the client would have to
guess or search for what it just made.

### How does it work?

`Cache-Control`'s value is a comma-separated list of directives. The four that matter:

- **`max-age=60`** — anyone holding this response may reuse it for 60 seconds without
  asking the server at all. Not even a conditional request — pure silence.
- **`public` / `private`** — *who* may store it. `public`: shared machines too (CDNs,
  proxies — caches serving many users). `private`: only the end user's own browser.
- **`no-cache`** — confusingly named: *store it, but revalidate before every reuse* —
  send the stored `ETag` back via `If-None-Match` and hope for a free `304`.
- **`no-store`** — the strict one: never write this to any cache, anywhere. For personal
  and sensitive data.

`Location` is just a URL. The pairing rule: `201` + `Location` = "your new thing lives
here"; `3xx` + `Location` = "go here instead". A good `201 Location` must be a URL that a
`GET` actually answers.

### The demo

```java
// creating an event now returns an address you can actually GET
return ResponseEntity
    .created(URI.create("/events/" + event.name()))  // 201 + Location
    .body(event);

// public data: reusable for a minute
.cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS)) // -> Cache-Control: max-age=60

// personal data: never store
.cacheControl(CacheControl.noStore())                    // -> Cache-Control: no-store
```

```text
=== 1) create an event: the Location header names the new thing ===
HTTP/1.1 201 
Location: /events/gig
{"name":"gig"}

=== 2) follow that Location with a GET: the thing is really there ===
HTTP/1.1 200 
{"name":"gig"}

=== 3) public data: venue says it may be reused for 60 seconds ===
HTTP/1.1 200 
ETag: "v-7d2a2ae9"
Cache-Control: max-age=60
{"name":"Old Hall","city":"Leeds"}

=== 4) personal data: organizer event says never store this ===
HTTP/1.1 200 
Cache-Control: no-store
{"name":"Coldplay Live","owner":"alice","published":true}
```

### Walkthrough

1. **Captures 1–2: the `Location` round trip.** The `201` answered *where the created
   thing lives*, and a `GET` to exactly that address returns it. (Honest note: until this
   lesson the toy app's `Location` pointed at a numeric index with no matching `GET`
   route — a URL nobody could follow. A `Location` you cannot `GET` is a broken promise.)
2. **Capture 3: a layered strategy in one response.** `max-age=60`: for a minute, reuse
   without asking — zero requests reach the server. When the minute expires, the `ETag`
   next to it offers layer two: revalidate conditionally, likely for a free `304`. Sixty
   seconds of silence, one cheap question, sixty more — full downloads only when content
   actually changes.
3. **Capture 4: the safety rule.** `no-store` on personal data: no browser cache, CDN, or
   proxy may keep a copy. `max-age=60` here could serve alice's private event to whoever
   asks a shared cache next — with personal data the storage instruction is a security
   control, not a performance tweak.

### Where is it used?

Public, read-heavy, rarely-changing data gets generous caching: event pages, venue
details, seat-map images — how a platform survives an on-sale crowd, with most requests
never reaching the server. Anything personalised — "my orders", "my tickets", anything
behind `Authorization` — rides with `no-store`. Every `201` should carry a `Location`:
when Phase 5 says "create the order and return its URL", this header is what it means.

### Common mistakes

- **Reading `no-cache` as "do not cache".** It means *store but revalidate every time*.
  "Do not store" is `no-store`. The two most-confused words in HTTP.
- **Cacheable headers on personal data.** A shared cache serving one user's `max-age`
  response to another user is a data leak — an incident, not a bug report.
- **A `Location` that cannot be fetched.** Pointing at a route that `404`s breaks every
  client that does the natural next thing.
- **Expecting `Cache-Control` to force behaviour.** It is an instruction sheet, not
  enforcement — curl ignores it entirely. Well-behaved caches obey; the header is how
  they know what obeying means.
- **Forgetting that `max-age` means total silence.** A change 5 seconds into the window
  leaves clients stale for the remaining 55 — `max-age` deliberately trades freshness for
  load.

### Summary

- `Cache-Control` directives: `max-age` (reuse silently), `public`/`private` (who may
  store), `no-cache` (store but always revalidate), `no-store` (never store).
- `max-age` + `ETag` compose: silence, then cheap `304`s, then a full download only on
  real change.
- `Location` on `201`: the created thing's address, which must be GETtable. On `3xx`: the
  redirect target.

### Practice questions

**Question 1.** Two endpoints need `Cache-Control` headers. First: `GET /events/5` — a
public event page, read thousands of times a minute during an on-sale, updated rarely.
Second: `GET /my/tickets` — the logged-in user's personal tickets, carrying their name
and seat numbers. Choose a sensible `Cache-Control` for each, and explain the specific
harm if the two choices were accidentally swapped.

**Answer.** `GET /events/5`: `Cache-Control: public, max-age=60` (or similar) — public
data, identical for every caller, read-heavy and rarely updated, so let browsers and
shared caches reuse it for a minute; thousands of refreshes cost the server almost
nothing, at the acceptable price of up to a minute's staleness. `GET /my/tickets`:
`Cache-Control: no-store` — personal data must never be written to any cache. Swapped
harms: with `max-age` on `/my/tickets`, a shared cache could store one user's tickets —
name and seats — and serve them to the next user who asks: a privacy leak and an
incident. With `no-store` on `/events/5`, every one of the thousands of on-sale refreshes
hits the server directly — caching disappears exactly when it was needed most, and load
that should have been absorbed by caches lands on the app.

**Question 2.** A client sends `POST /orders` with a valid body and receives
`201 Created` with `Location: /orders/812`, plus the order's JSON in the response body.
Explain what the `Location` header is telling the client, what a reasonable client might
do with it later, and how the *same* header means something different when it arrives on
a `302` response instead.

**Answer.** On the `201`, `Location: /orders/812` says: "the order you just created now
lives at this URL — the server chose the id, and this is the address." A reasonable
client stores that URL as its handle on the order: later it can `GET /orders/812` to
check status, poll it while the order is processed, or link the user straight to it. On a
`302`, the same header is not about anything created — it means "what you asked for is
temporarily somewhere else; resend your request to this URL instead", and clients like
browsers (or `curl -L`) follow it automatically. One header, two meanings, and the status
code beside it is what says which: `201` = "your new thing's address", `3xx` = "go here
instead".

---

## Lesson 29 — Retry-After and Idempotency-Key: making retries polite and safe

### What is it?

Two headers that tame retries, one per side. **`Retry-After`**: the *server* telling a
refused client how long to wait before trying again — sent with `429 Too Many Requests`
and often with `503 Service Unavailable`. **`Idempotency-Key`**: the *client* attaching a
self-generated unique value to a request, so that if it must send the same request twice
— after a timeout — the server can recognise the repeat and answer again *without redoing
the work*.

### Why do we need it?

Retries are both necessary and dangerous. Necessary: networks lose replies; a client that
never retries just fails. Dangerous twice: retrying *too eagerly* hammers a server that
asked for mercy, and retrying a *creation* can duplicate it. The lost-reply problem: a
client sends `POST /orders`, the reply never arrives, and the client cannot know which
world it is in — order never created, or created with the confirmation lost. Resending
risks a double order; not resending risks no order. `Retry-After` fixes the *when*;
`Idempotency-Key` fixes the *whether it is safe*.

### How does it work?

**`Retry-After`**: a refusal arrives with a number of seconds. A polite client waits that
long, then tries again. The server knows when capacity returns, so the server says.

**`Idempotency-Key`**, a three-step contract:

1. The client generates a unique value per *logical operation* — "this particular
   purchase" — and sends it: `Idempotency-Key: key-7f3a`.
2. The server, on first sight of a key, does the work and *stores the result under that
   key*.
3. If the same key arrives again, the server skips the work and replays the stored
   answer.

Effect: a `POST` — not idempotent by nature — behaves as if it were. The client may retry
a timed-out purchase any number of times; at most one order can exist.

### The demo

A rate-limited endpoint (3 calls per 10 seconds) and an order creator honouring keys:

```java
@PostMapping("/orders")
ResponseEntity<Order> createOrder(@RequestBody Map<String, String> body,
        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    if (key != null && ordersByKey.containsKey(key)) {
        Order existing = ordersByKey.get(key);           // seen this exact request:
        return ResponseEntity.status(201)                // replay the original answer,
            .location(URI.create("/orders/" + existing.id()))
            .body(existing);                             // do NOT create again
    }
    Order order = new Order(orders.size() + 1, body.get("item"));
    orders.add(order);
    if (key != null) {
        ordersByKey.put(key, order);                     // remember: key -> result
    }
    return ResponseEntity.status(201)
        .location(URI.create("/orders/" + order.id()))
        .body(order);
}
```

The live session:

```text
=== A) five rapid calls to a rate-limited endpoint (3 per 10s allowed) ===
200 200 200 429 429 
--- the refusal in full ---
HTTP/1.1 429 
Retry-After: 10
{"error":"too many requests, slow down"}
--- obeying Retry-After: wait 10 seconds, ask again ---
HTTP/1.1 200 
{"message":"served"}

=== B) POST an order WITH an Idempotency-Key ===
HTTP/1.1 201 
Location: /orders/1
{"id":1,"item":"2 tickets, Coldplay"}

=== C) the retry: EXACT same request again (same key) ===
HTTP/1.1 201 
Location: /orders/1
{"id":1,"item":"2 tickets, Coldplay"}

=== D) all orders after the "duplicate": still just one ===
[{"id":1,"item":"2 tickets, Coldplay"}]

=== E) two posts WITHOUT a key: the duplicate goes through ===
[{"id":1,...},{"id":2,"item":"1 ticket, Opera"},{"id":3,"item":"1 ticket, Opera"}]
```

### Walkthrough

1. **A:** three served, two refused — and the refusal names the waiting time. The capture
   obeys: 10 seconds later, served again. Ignoring the header just buys more `429`s.
2. **B and C are byte-for-byte identical requests** — exactly what a retry loop sends
   after a lost reply. Both got `201`, the *same* order id, the same `Location`. The
   client cannot tell which send did the creating — and no longer needs to.
3. **D is the proof:** two sends, one order. Work done once, answer replayed once.
4. **E is the control experiment:** the same double-send without keys — orders 2 and 3
   are the double-charge bug, live. One missing header is the whole difference.

Design note: the key identifies the *operation*, not the attempt. A fresh key per retry
defeats the mechanism; one purchase, one key, reused across all its retries.

### Where is it used?

`Retry-After` guards every serious public API's rate limits. `Idempotency-Key` is the
industry pattern for money: Stripe requires it on charge-creating calls and stores
results for 24 hours, precisely so a timed-out charge can retry without double-billing.
The Phase 5 capstone implements this pattern for ticket orders.

### Common mistakes

- **Ignoring `Retry-After` and retrying instantly.** Pure waste for one client; a retry
  storm when a thousand do it together.
- **A fresh key per retry.** The retry no longer looks like a repeat; duplicates return.
- **Reusing one key for different operations.** The second request gets the first one's
  stored answer — opera buyer receives a Coldplay confirmation. Unique per logical
  operation.
- **Forgetting the server must store results.** The client merely triggers the feature;
  real servers store keys with an expiry (Stripe: 24 hours) in a database or cache.

### Summary

- `Retry-After`: the server names the waiting time; obey it.
- `Idempotency-Key`: client-generated, per-operation; the server stores the first result
  and replays it for repeats — making `POST` retry-safe.
- With a key: two sends, one order. Without: two sends, two orders. That is the whole
  mechanism.

### Practice questions

**Question 1.** A ticket-buying client sends `POST /orders` with
`Idempotency-Key: buy-991` and the request times out — no reply at all. It retries with
the same key and receives `201` with order id 14. A teammate reviewing the code worries:
"but what if the *first* request actually succeeded on the server before the timeout? Now
we've sent the request twice — did we just order twice?" Explain what the server did in
each of the two possible worlds (first request never arrived versus first request
succeeded silently), and why the client ends up with exactly one order either way.

**Answer.** World one — the first request never reached the server: the retry is the
first time the server sees `buy-991`, so it creates order 14, stores the key against that
result, and answers `201`. One order. World two — the first request succeeded but its
reply was lost: the server already created order 14 *and stored it under `buy-991`* when
the first request landed; when the retry arrives carrying the same key, the server finds
it in the store, skips creation entirely, and replays the stored answer — the same `201`,
the same order 14. One order. In both worlds exactly one creation happened, and the
client receives an identical response, so it cannot tell which world it was in — which is
precisely the point: the idempotency key makes the unanswerable question ("did my first
attempt land?") stop mattering.

**Question 2.** During a flash sale, a client gets `429 Too Many Requests` with
`Retry-After: 30`. The developer "fixes" it with an instant-retry loop: on `429`,
immediately resend, forever, until a `200` comes back. Describe what this loop does to
the client's own chances of being served and to the server, and what the correct
behaviour would be — including what should happen if the retry after 30 seconds meets
*another* `429`.

**Answer.** The loop fires requests as fast as the network allows, and every one of them
lands inside the blocked window, so every one earns another `429` — the client burns CPU
and bandwidth to receive refusals, and if the limiter counts refused attempts against the
quota (many do), each retry pushes its own service further away. For the server, one such
client is noise; a flash-sale crowd of them is a self-inflicted stampede — thousands of
clients hammering a limiter that exists to shed load, turning a controlled refusal into
real pressure. Correct behaviour: read `Retry-After: 30`, wait the full 30 seconds, retry
once. If that retry meets another `429` with a new `Retry-After`, obey the new value too
— and keep a sensible retry budget (give up after a few rounds and show the user an
honest "busy, try later") rather than looping forever. The server names the waiting time
because only it knows when capacity returns; the client's job is to listen.

---

## Lesson 30 — Authorization and bearer tokens: identity in a world with no memory

### What is it?

**`Authorization`** is the request header where credentials travel — usually
`Authorization: Bearer <token>`, where a **bearer token** is a string that proves who you
are. "Bearer" is the operative word: whoever *bears* (holds) the token is treated as that
identity, the way whoever holds a concert ticket gets the seat. The header exists because
**HTTP remembers nothing between requests** — if identity matters, it must arrive *in
every single request*.

### Why do we need it?

Statelessness is how HTTP works, and identity has to live somewhere. The server keeps no
notebook saying "the person on this connection is alice"; the next request starts from
zero. That is the design that lets a platform scale: if no request depends on server
memory of a previous one, *any* of ten identical copies behind a load balancer can answer
*any* request. Identity-in-every-request is the price; `Authorization` is where it is
paid.

### How does it work?

1. Once, at login, the client proves itself properly and receives a token.
2. From then on, the client attaches `Authorization: Bearer <token>` to **every** request
   that needs an identity.
3. The server validates the token *from scratch each time*, extracts the identity,
   handles the request — and forgets.
4. A request without the header is a stranger: `401` + `WWW-Authenticate`, even if the
   same client was served one second earlier.

The opposite design is a **session**: server-side memory of a user between requests. HTTP
has none by default; older web apps built sessions deliberately (a notebook entry per
logged-in user, the browser carrying a reference to it). Tokens flip it: the proof
travels with the request, the server keeps no notebook. Phase 2 builds this properly with
JWTs — tokens cryptographically signed so the server can verify without looking anything
up.

### The demo

A `whoami` endpoint whose entire worldview is the header in front of it:

```java
@GetMapping("/whoami")
ResponseEntity<?> whoami(
        @RequestHeader(value = "Authorization", required = false) String auth) {
    if (auth == null) {
        return ResponseEntity.status(401)
            .header("WWW-Authenticate", "Bearer")
            .body(Map.of("error", "no credentials"));
    }
    String user = auth.replace("Bearer ", "");   // toy token: just a name
    return ResponseEntity.ok(Map.of("you", user));
}
```

```text
=== 1) no Authorization header: the server has no idea who this is ===
HTTP/1.1 401 
WWW-Authenticate: Bearer
{"error":"no credentials"}

=== 2) same endpoint, alice token ===
{"you":"alice"}

=== 3) same endpoint, bob token ===
{"you":"bob"}

=== 4) alice asks... then the VERY NEXT request omits the header ===
{"you":"alice"}
HTTP/1.1 401 
   (no memory of alice: one request earlier she was right here)

=== 5) the toy weakness: ANYONE can claim to be alice ===
{"name":"Coldplay Live","owner":"alice","published":true}
```

### Walkthrough

1. **Captures 1–3: one endpoint, three answers, zero state.** Only the header varied —
   the task's "when it works" line verbatim: *the same endpoint behaving differently
   purely because of a header you changed, with no server-side state anywhere.*
2. **Capture 4 is statelessness made visible.** Alice is served; the very next request
   omits the header and is a complete stranger. The server has nothing to remember her
   *with* — no notebook exists.
3. **Capture 5 is the toy's honest limitation.** Our "token" is literally the name
   `alice`, so anyone who types it is alice. A real token is a long, unguessable,
   cryptographically signed string — forgery fails. But even then, whoever **holds** a
   real token wields it: theft works where forgery fails. That is why bearer tokens only
   travel over HTTPS — an unencrypted token on the wire is a stolen identity in transit.

### Where is it used?

Every authenticated API call: the frontend stores the token after login and attaches it
to every fetch. The known flows complete the picture: expired token → `401` → silent
refresh and retry; valid token, forbidden action → `403`, where re-authenticating helps
nothing. Horizontal scaling works *because* every request is self-contained: any instance
validates any token on any request.

### Common mistakes

- **Assuming login "opens a session" on an API.** After login you hold a token; the
  server holds nothing. Forget the header once and that request is a stranger.
- **Sending a token over plain HTTP.** Bearer means *holder wins*; an eavesdropper who
  copies the header owns the identity until it expires. HTTPS only, always.
- **Logging the `Authorization` header.** Logs containing tokens are a credential
  database nobody meant to build. Mask it.
- **Hidden state between requests.** If request B only works after request A, something
  is remembered server-side, and it breaks when a load balancer routes A and B to
  different instances. Each request must carry everything it needs.

### Summary

- `Authorization: Bearer <token>` carries identity in every request, because HTTP
  remembers nothing.
- Bearer = holder wins: real tokens defeat forgery, not theft — hence HTTPS only.
- No sessions by default; token-carried identity is why ten identical instances can share
  any workload. This closes Task F4.4.

### Practice questions

**Question 1.** A frontend developer logs in via `POST /login`, receives a token, and
then calls `GET /my/tickets` *without* any `Authorization` header — reasoning "I just
logged in two seconds ago on this same connection, the server knows it's me." The server
answers `401`. Explain why the server is behaving correctly, what the login actually gave
the client, and what the correct version of the request looks like.

**Answer.** The server is stateless: it keeps no memory of who logged in, on what
connection, or when — each request is judged entirely on its own contents, and this
request contained no credentials, so `401 Unauthorized` ("I do not know who you are") is
the only correct answer. The login did not "open" anything on the server; it *gave the
client a token* — a portable proof of identity that the client is responsible for
attaching to every subsequent request. The correct request is:
`GET /my/tickets` with the header `Authorization: Bearer <the token from login>`. Same
endpoint, same client, one header added — and the stranger becomes a known user, this
request only.

**Question 2.** A ticketing platform runs ten identical copies of its server behind a
load balancer that sends each request to whichever copy is least busy. Explain why
`Authorization: Bearer <token>` on every request makes this setup work with no
coordination between the copies — and why the older alternative, a server-side session
(the server keeping an in-memory notebook of logged-in users), breaks or complicates it.

**Answer.** With a bearer token, every request is self-contained: whichever copy receives
it has everything needed — it validates the token, extracts the identity, answers, and
forgets. Copy three can serve a user's first request and copy eight their second; neither
knows nor cares what the other did, so no coordination, no shared memory, and adding an
eleventh copy is just switching it on. With in-memory sessions, the notebook entry for a
logged-in user lives *inside one particular copy*: if the load balancer sends the next
request to a different copy, that copy's notebook has no entry — the user is suddenly
logged out mid-session. The workarounds are all costly: pin each user to one copy
("sticky sessions", which defeats least-busy balancing and loses the user if their copy
dies), or move the notebook into a shared store that every copy queries (extra
infrastructure and a lookup on every request). Token-carried identity removes the
notebook entirely — which is exactly why stateless APIs scale horizontally so easily.

# Task F4.3 — Status codes, and the ones beginners get wrong

## Lesson 18 — Status codes: the five families

### What is it?

The **status code** is the three-digit number in a response's first line. Its **family**
is the first digit: `1xx` information, `2xx` it worked, `3xx` go elsewhere, `4xx` the
client got it wrong, `5xx` the server got it wrong.

### Why do we need it?

There are dozens of codes; nobody holds them all in memory. From the first digit alone a
client knows the three things that matter most: did it work, whose fault is it if not, and
is retrying sensible? Dashboards, retry policies and error handlers branch on the family
first and the exact code second.

### How does it work?

| Family | Plain sentence | What the client does |
| --- | --- | --- |
| `1xx` | "A note before the real answer: still working / go ahead." | Keeps waiting. Rare; tools handle it invisibly. |
| `2xx` | "It worked." | Proceeds, uses the body if any. |
| `3xx` | "What you want is elsewhere — or you already have it." | Follows the new address, or reuses its saved copy. Normal flow. |
| `4xx` | "Your request is faulty." | Fixes the request. **Resending unchanged fails again.** |
| `5xx` | "Your request was reasonable. I failed." | May retry later. |

The retry rule in the last two rows is the key consequence: `4xx` = don't retry unchanged;
`5xx` = retrying may help.

### Simple example (run for real)

For the `5xx`, the toy app got a `/boom` endpoint whose handler deliberately throws:

```java
@GetMapping("/boom")
String boom() {
    throw new RuntimeException("simulated bug in the handler");
}
```

Captures:

```
=== 2xx: GET /hello ===
HTTP/1.1 200 
=== 3xx: GET /docs (no trailing slash) on the python file server ===
HTTP/1.0 301 Moved Permanently
=== 4xx: GET /no-such-path ===
HTTP/1.1 404 
=== 5xx: GET /boom (handler throws) ===
HTTP/1.1 500 
{"timestamp":"2026-10-21T17:59:06.199+00:00","status":500,"error":"Internal Server Error","path":"/boom"}
```

### Walkthrough

1. `200` — it worked; read the body.
2. `301` — go elsewhere: `/docs` really lives at `/docs/`. Not a failure; traffic
   direction. Mechanics in a later lesson.
3. `404` — the client's fault: the path does not exist. Retrying unchanged produces `404`
   forever; fixing the path is the move.
4. `500` — the server's fault: the handler crashed. The request was flawless; Spring
   caught the exception and said "that one's on me". A retry might succeed; the request
   needs no fixing.

### Where is it used?

- Error graphs are drawn per family: a `4xx` spike means confused clients; a `5xx` spike
  means a broken service. Different people get woken up.
- Retry libraries retry `5xx` and never `4xx`, straight from this table.

### Common mistakes

- **Memorising codes as unrelated trivia.** `422` is hard alone, easy as "a 4xx".
- **Retrying `4xx`.** The request is faulty; repetition does not repair it.
- **Treating every non-`200` as an error.** `3xx` is healthy traffic; one member ("you
  already have it") is a performance feature.

### Summary

- First digit = category: info / worked / elsewhere / client fault / server fault.
- `4xx`: fix the request, do not resend unchanged. `5xx`: retrying may help.
- Unknown codes degrade gracefully: treat them by family.

### Practice questions

**Question 1.** Four responses arrive: `503`, `422`, `301`, `204`. For each one, name its
family, give the family's one-sentence meaning, and answer: could sending the identical
request again plausibly get a different result?

**Answer.** `503`: family 5xx, "your request was fine, the server failed (here: is
declining work right now)" — yes, a retry later can succeed, because the fault is
temporary and server-side. `422`: family 4xx, "your request is faulty" — no, the identical
request fails identically; it must be fixed first. `301`: family 3xx, "what you want is
elsewhere" — resending gets the same redirect again; the useful move is following the new
address, not retrying. `204`: family 2xx, "it worked (nothing to show)" — there is nothing
to retry; the operation already succeeded.

**Question 2.** A client library receives status code `462` from a server. The code
appears in no documentation anywhere — the server team invented it. Using only the family
system, what should the library conclude about whose fault this was, and should its
automatic retry fire? Explain the reasoning.

**Answer.** The first digit is 4, so the family verdict applies: the request itself was
faulty from the server's point of view — client-side fault. The automatic retry must not
fire: a faulty request resent unchanged is faulty again, and retrying would hammer the
server without ever succeeding. This graceful degradation is exactly why the family system
exists: a client that has never seen a specific code can still behave correctly by reading
the first digit alone.

## Lesson 19 — Four flavours of "it worked": 200, 201, 202, 204

### What is it?

The `2xx` family all means success, but four members say different sentences. `200 OK`:
"done — here is the result." `201 Created`: "a new thing now exists — here is where it
lives." `202 Accepted`: "I will do it — it is not done yet." `204 No Content`: "done —
deliberately nothing to show."

### Why do we need it?

"It worked" alone leaves a client guessing: did you create something, and where? Is the
work finished? Is there a body to parse? The four codes answer in the status line, before
the client touches the body.

### How does it work?

| Code | Says | Body? | Extra detail |
| --- | --- | --- | --- |
| `200 OK` | Done; result enclosed. | Yes | General-purpose success for reads. |
| `201 Created` | A new resource exists. | Usually the created thing | Comes with a **`Location` header**: the new thing's URL. |
| `202 Accepted` | Accepted; work continues after this reply. | A progress hint | The honest code for slow jobs; point at somewhere to check progress. |
| `204 No Content` | Done; nothing to show, on purpose. | **None at all** | Natural answer to `DELETE`; client must not parse a body. |

The deep split is `201` versus `202`, and it is about **time**. `201` is past tense: the
thing is made. `202` is future tense: completion happens after this conversation. The
choosing question: *is the work finished when I reply?*

### Simple example (run for real)

`POST /events` was upgraded to its proper behaviour (`201` + `Location`), and a pretend
refund endpoint added for the `202`:

```java
@PostMapping("/events")
ResponseEntity<ToyEvent> create(@RequestBody ToyEvent event) {
    events.add(event);
    return ResponseEntity
        .created(URI.create("/events/" + (events.size() - 1)))  // 201 + Location
        .body(event);
}

@PostMapping("/refunds")
ResponseEntity<RefundStatus> startRefund() {
    return ResponseEntity
        .accepted()                        // 202: accepted, still working on it
        .header("Location", "/refunds/1") // where to check progress
        .body(new RefundStatus("processing"));
}
```

Captures:

```
=== 200: GET /hello ===
HTTP/1.1 200 
Content-Type: application/json

{"message":"hello","id":1}

=== 201: POST /events ===
HTTP/1.1 201 
Location: /events/0
Content-Type: application/json

{"name":"Coldplay Live"}

=== 202: POST /refunds ===
HTTP/1.1 202 
Location: /refunds/1
Content-Type: application/json

{"status":"processing"}

=== 204: DELETE the event we just created ===
HTTP/1.1 204 
```

### Walkthrough

1. `200`: a read succeeded; the body is the answer.
2. `201`: the event exists; `Location: /events/0` hands the client its address — no
   guessing how this server builds URLs. Past tense.
3. `202`: the refund has *not happened* — a payment provider confirms it later. Claiming
   `201` would make clients tell customers "refund complete". Future tense, honestly
   stated, plus a place to check.
4. `204`: success with no body — not even a `Content-Type`, because there is no content to
   describe.

### Where is it used?

- Fetch an event → `200` with the event.
- Create an event → `201` with `Location` — creation completes synchronously.
- Delete an event → `204`.
- Start a refund → `202` plus somewhere to check.

### Common mistakes

- **`200` for everything.** Discards information the protocol already solved.
- **`201` without `Location`.** The client reverse-engineers your URL scheme, which breaks
  when you change it.
- **`200` with an empty body instead of `204`.** JSON parsers choke on empty strings.
- **Lying about time.** `201` for unfinished work or `202` for finished work both mislead
  clients into wrong promises.

### Summary

- `200` result enclosed; `201` created + `Location`; `202` accepted, finishing later;
  `204` done, no body.
- `201` vs `202` = past tense vs future tense.
- `Location` on a `201` is the client's address for the new thing.
- `204` means "parse nothing".

### Practice questions

**Question 1.** Pick the best status code (`200`, `201`, `202`, or `204`) for each
response, and say which of the four responses carry a body:

1. `GET /events/7` — the server returns the event's JSON.
2. `POST /events` — the event is fully saved before the server replies, and the reply
   includes the new event and its address.
3. `DELETE /events/7` — the deletion succeeded; the server has nothing to say about it.
4. `POST /refunds` — the server has queued a refund that its payment provider will confirm
   tomorrow.

**Answer.** (1) `200` — a successful read, body = the event. (2) `201` — creation finished
before the reply; body = the new event, plus a `Location` header with its address.
(3) `204` — success with deliberately no body. (4) `202` — accepted but not finished;
body = a progress hint such as `{"status":"processing"}`. Responses 1, 2 and 4 carry
bodies; response 3 carries none.

**Question 2.** An API returns `201` for `POST /orders`, with the new order in the body
but no `Location` header and no id field anywhere in the response. The client now wants to
poll the new order's status every minute. What problem does the client face, and what are
two reasonable ways the API could fix it — one using a header, one using the body?

**Answer.** The client has no address to poll. It knows an order exists but not where it
lives, so its only option is guessing the server's URL pattern — fragile guesswork that
breaks the day the server reorganises its paths. Header fix: return
`Location: /orders/{id}` on the `201`, the standard way of handing over the new thing's
address. Body fix: include the order's identity in the JSON — an `id` field (letting the
client build the documented path) or a full self-address field. Either restores the
ability to poll; the `Location` header is the convention every HTTP client already
understands.

## Lesson 20 — Elsewhere, or already yours: 301, 302, 304

### What is it?

A **redirect** is a response saying "what you want is at this other URL", with that URL in
the `Location` header. `301`: moved **for good** — update your links. `302`:
**temporarily** elsewhere — keep using the old URL next time. `304 Not Modified` is the
family's odd member: "you already have the current copy — I am not resending it".

### Why do we need it?

URLs are promises, but things move. Without redirects, every old link in every email,
bookmark and search result would break when anything moved. And `304` fixes a different
waste: thousands of refreshes of an unchanged page do not need the same bytes resent
thousands of times.

### How does it work?

Redirect: client asks at the old URL → server answers `301`/`302` with
`Location: <new URL>`, usually no body → client asks again at the new address. Browsers
follow automatically; `curl` only with `-L`.

The difference between `301` and `302` is **what the client remembers afterwards**:

- **`301`:** update bookmarks; search engines rewrite their index; caches may remember the
  forward so firmly they stop checking the old URL. Hard to undo.
- **`302`:** remember nothing; ask at the original URL again next time.

**`304`** answers only a **conditional request** — one where the client attached proof of
which version it has, and the server compared. Nothing changed → `304`, no body, meaning
"display your saved copy". Full mechanics in the headers task.

### Simple example (run for real)

Two redirecting endpoints in the toy app:

```java
@GetMapping("/ancient-hello")
ResponseEntity<Void> ancientHello() {
    return ResponseEntity.status(301)                 // moved for good
        .header("Location", "/hello").build();
}

@GetMapping("/old-hello")
ResponseEntity<Void> oldHello() {
    return ResponseEntity.status(302)                 // temporarily elsewhere
        .header("Location", "/hello").build();
}
```

Session:

```
=== 301: GET /ancient-hello ===
HTTP/1.1 301 
Location: /hello

=== 302: GET /old-hello ===
HTTP/1.1 302 
Location: /hello

=== same URL with -L: curl follows the Location ===
{"message":"hello","id":1}
```

And a real `304` from the Python file server: the first fetch of `logo.txt` returned `200`
plus `Last-Modified: Wed, 21 Oct 2026 18:34:41 GMT`; the second fetch sent that timestamp
back:

```bash
curl -si -H "If-Modified-Since: Wed, 21 Oct 2026 18:34:41 GMT" http://localhost:8000/logo.txt
```

```http
HTTP/1.0 304 Not Modified
```

### Walkthrough

1. The two redirects look identical on the wire except the number — and the number only
   matters for the *future*: after `301`, clients go to `/hello` directly from now on;
   after `302`, they return to `/old-hello` every time.
2. `curl -L` compressed the dance: hit `/old-hello`, read `Location`, asked `/hello`,
   printed its JSON. Without `-L` you see only step one — remember that when an API
   "returns nothing".
3. The `304`: the client proved which version it had; the server refused to resend. No
   body — "display what you already have". For an event page refreshed by thousands, that
   is megabytes saved per minute.

### Where is it used?

- `301`: renamed event URLs; the classic site-wide `http://` → `https://` upgrade.
- `302`: a temporary maintenance detour nobody should memorise.
- `304`: the on-sale page refresh storm, answered nearly for free.

### Common mistakes

- **`301` for something temporary.** The web memorises it; undoing takes months. When
  unsure, `302` is the recoverable mistake.
- **Redirecting without `Location`.** "Go elsewhere" without saying where.
- **Treating `304` as a failure.** It is the cheapest success in HTTP.
- **Forgetting `-L` in curl.** Unfollowed redirects look like empty responses.

### Summary

- `301` = moved for good (world updates its records); `302` = temporarily elsewhere
  (nobody updates anything).
- The new address travels in `Location`; redirect bodies are usually empty.
- `304` = "your copy is current", answers conditional requests, saves the body bytes.
- `curl` needs `-L` to follow redirects.

### Practice questions

**Question 1.** During a venue outage, a ticketing site sends visitors of `/box-office` to
`/backup-box-office` — but the developer uses a `301`. The outage ends a day later and
`/box-office` works again. Weeks later, many visitors still land on the backup page.
Explain why they keep landing there, which code should have been used, and what that code
tells clients to remember.

**Answer.** `301` means "moved for good", and clients took it at its word: browsers
updated bookmarks and cached the forwarding, search engines rewrote their index entries —
many clients stopped checking `/box-office` at all. Those memorised forwards keep firing
long after the outage, and the site cannot order the world's caches to forget. The right
code was `302`, "temporarily elsewhere": it tells clients to remember nothing and to keep
using the original URL next time, so the detour would have ended the moment the server
stopped sending it.

**Question 2.** Here is an exchange. First request: `GET /logo.png` → response `200 OK`
with the image and a header `Last-Modified: Tue, 01 Oct 2026 10:00:00 GMT`. Second
request, a day later: `GET /logo.png` with the header
`If-Modified-Since: Tue, 01 Oct 2026 10:00:00 GMT` → response `304 Not Modified`, no body.
What did the `304` save compared to a `200`, and what does the client display, given that
the response contained no image at all?

**Answer.** The `304` saved the entire response body — the image bytes. Only a small
header-only reply travelled, instead of the full picture being resent. The client displays
the copy it saved from the first fetch: that is the whole arrangement. The client said
"I have the version from 10:00 on 1 October — only send bytes if something newer exists",
the server compared timestamps, found nothing newer, and answered "your copy is current".
No body is needed, because the body already sits in the client's cache.

## Lesson 21 — Three ways a request can be wrong: 400, 422, 409

### What is it?

Three codes that all say "no", for different reasons. `400 Bad Request`: "I could not even
read that." `422 Unprocessable Entity`: "I read it fine, but the values are invalid on
their own terms." `409 Conflict`: "the request is fine — it collides with the world's
current state."

### Why do we need it?

Each code tells the client **what to fix**. `400`: fix your bytes. `422`: fix your values.
`409`: fix nothing — you lost a race; refetch the state and re-decide. Collapsing all
three into `400` leaves clients guessing.

### How does it work?

The server climbs a three-question ladder:

1. **Can I parse the body?** No → `400`. Fails in the parsing machinery, before
   application logic runs.
2. **Do the values make sense by themselves?** No → `422`. Blank name, price of minus
   ten, event ending before it starts — wrong regardless of the database.
3. **Does it collide with current state?** Yes → `409`. Someone else got the seat first.

The deep split: **400 and 422 are timeless faults of the request** — identical bytes fail
identically forever. **409 is a fault of timing** — the identical request might succeed
later.

### Simple example (run for real)

A blank-name check on `POST /events`, and a seat-hold endpoint — ticketing's canonical
`409`:

```java
@PostMapping("/events")
ResponseEntity<?> create(@RequestBody ToyEvent event) {
    if (event.name() == null || event.name().isBlank()) {
        return ResponseEntity.unprocessableEntity()              // 422: read it fine,
            .body(Map.of("error", "name must not be blank"));    // but invalid values
    }
    events.add(event);
    return ResponseEntity
        .created(URI.create("/events/" + (events.size() - 1)))
        .body(event);
}

@PostMapping("/seats/{seat}/hold")
ResponseEntity<?> holdSeat(@PathVariable String seat, @RequestBody Holder holder) {
    if (seatHolds.containsKey(seat)) {
        return ResponseEntity.status(409)                        // conflict with
            .body(Map.of("error", "seat " + seat + " is already held"));  // current state
    }
    seatHolds.put(seat, holder.name());
    return ResponseEntity.status(201)
        .body(Map.of("seat", seat, "heldBy", holder.name()));
}
```

Session:

```
=== 400: body is not valid JSON ===            <- sent: {"name": }
HTTP/1.1 400 
{"timestamp":"...","status":400,"error":"Bad Request","path":"/events"}

=== 422: valid JSON, blank name ===            <- sent: {"name":""}
HTTP/1.1 422 
{"error":"name must not be blank"}

=== 201: alice holds seat H7 ===
HTTP/1.1 201 
{"seat":"H7","heldBy":"alice"}

=== 409: bob tries the same seat ===
HTTP/1.1 409 
{"error":"seat H7 is already held"}
```

### Walkthrough

1. **The `400`:** `{"name": }` is not JSON. The parser gave up; Spring's default error
   answered; my handler never ran.
2. **The `422`:** `{"name":""}` parsed fine; my check rejected it — wrong no matter what
   the database holds.
3. **The `201`/`409` pair proves what `409` means:** bob's request was identical in
   quality to alice's. The only difference was *when it arrived*. A collision with state,
   not a defect.
4. Retries: resending `400`/`422` bytes unchanged is pointless forever. Bob's request
   could succeed later — but the right move is refetching state and re-deciding, not
   hammering.

### Where is it used?

Trailing-comma body → `400`. Price of minus ten, event ending before it starts → `422`.
Seat already held → `409`. Seat holds are the heart of Phase 5; `409` is its everyday
answer.

### Common mistakes

- **`400` for everything.** The client cannot tell broken bytes from bad values.
- **`422` for conflicts.** Tells bob to "fix" a flawless request; hides that the seat may
  free up.
- **Blind instant retry of `409`.** Refetch, re-decide, then maybe re-ask.
- **Checking conflicts before validity.** Validate the request's own sense first, then
  touch state.

### Summary

- `400`: unreadable. `422`: readable, invalid on its own terms. `409`: valid, lost a race.
- 400/422 are timeless request faults; 409 is timing.
- After `409`: refetch state. After `400`/`422`: change the request.
- The same bytes earned alice `201` and bob `409` — that is `409`'s whole meaning.

### Practice questions

**Question 1.** Assign `400`, `422`, or `409` to each failure, with a one-line reason:

1. `POST /events` with body `{"name": "Gig",}` — note the trailing comma.
2. `POST /events` with body `{"starts":"2026-07-14T20:00","ends":"2026-07-13T22:00"}` —
   it ends a day before it starts.
3. `POST /seats/H7/hold` with a flawless body — but another customer's hold on H7 is
   active.

**Answer.** (1) `400`: the trailing comma makes the body unparseable; the failure happens
in the JSON parser, before any application logic. (2) `422`: the JSON parses perfectly,
but the values are nonsense on their own terms — an event cannot end before it starts, no
matter what is in the database. (3) `409`: nothing is wrong with the request; it collides
with the current state, because another customer's hold is active. Same seat, different
moment, and it would have succeeded.

**Question 2.** Bob's `POST /seats/H7/hold` just returned `409` because alice holds the
seat. Separately, carol's `POST /events` with `{"name":""}` just returned `422`. Both
clients are considering resending the exact same request later. For each, say whether the
identical request could ever succeed, why, and what the client should do instead of blind
resending.

**Answer.** Bob's identical request could succeed: `409` is a fault of timing, and if
alice's hold expires or she releases the seat, H7 is free again. But blind resending is
still wrong — the client should refetch the current state (which seats are free now?),
show the user, and let a fresh decision drive the next request. Carol's identical request
can never succeed: `422` is a timeless fault of the request itself — a blank name is
invalid regardless of state, today and forever. Her client must change the request: supply
a real name. Resending unchanged bytes after a `422` is a loop with no exit.

---

## Lesson 22 — Who are you, you may not, and there is nothing here: 401, 403, 404

### What is it?

Three codes that sort out identity and permission. `401 Unauthorized`: "I do not know who
you are" — no credentials, or expired ones. `403 Forbidden`: "I know exactly who you are,
and you still may not do this." And a deliberate use of `404 Not Found`: answering
"nothing here" about things the caller is not allowed to even know exist.

### Why do we need it?

Each code triggers a different client reaction. A `401` says: *get credentials* — show the
login screen, or refresh the expired token, then retry. A `403` says: *credentials will
not help* — you are logged in fine; this is simply not yours to touch. And the
`403`-versus-`404` choice is a security decision: a `403` on a secret thing *confirms the
thing exists*, which can be exactly the leak you were trying to prevent.

### How does it work?

A question ladder, climbed after the request parses fine:

1. **Do I know who you are?** No → `401`, plus a `WWW-Authenticate` header hinting how to
   present credentials. (The official phrase "Unauthorized" misleads; read it as
   *unauthenticated*.)
2. **I know you. Are you allowed this action?** No → `403`. Identity is settled;
   permission is the problem.
3. **Is even the existence of this thing private?** If yes, skip `403` and answer `404` —
   exactly what the server would say if the thing did not exist. The caller learns nothing.

Rule of thumb for step 3: `403` is fine when existence is public knowledge (anyone can
see the event; only the owner may delete it). `404` is right when existence itself is the
secret (another organiser's unpublished event).

### The demo

The toy app got a pretend-login world. Identity is a toy: `Authorization: Bearer alice`
just carries a name — real cryptographic tokens arrive in Phase 2. Two events exist:
event 1, published, owned by alice; event 2, **unpublished**, owned by bob. Handler rules:
no header → `401`; unpublished events are invisible to everyone but their owner → `404`;
deleting is owner-only → `403`.

```java
record OrganizerEvent(String name, String owner, boolean published) {}

private final Map<Integer, OrganizerEvent> organizerEvents = new HashMap<>(Map.of(
    1, new OrganizerEvent("Coldplay Live", "alice", true),
    2, new OrganizerEvent("Secret Launch Party", "bob", false)));

@GetMapping("/organizer-events/{id}")
ResponseEntity<?> getOrganizerEvent(@PathVariable int id,
        @RequestHeader(value = "Authorization", required = false) String auth) {
    if (auth == null) {
        return ResponseEntity.status(401)                    // I do not know who you are
            .header("WWW-Authenticate", "Bearer")
            .body(Map.of("error", "no credentials"));
    }
    String user = auth.replace("Bearer ", "");
    OrganizerEvent event = organizerEvents.get(id);
    if (event == null || (!event.published() && !event.owner().equals(user))) {
        return ResponseEntity.status(404)                    // hide that it exists
            .body(Map.of("error", "no such event"));
    }
    return ResponseEntity.ok(event);
}

@DeleteMapping("/organizer-events/{id}")
ResponseEntity<?> deleteOrganizerEvent(@PathVariable int id,
        @RequestHeader(value = "Authorization", required = false) String auth) {
    if (auth == null) {
        return ResponseEntity.status(401)
            .header("WWW-Authenticate", "Bearer")
            .body(Map.of("error", "no credentials"));
    }
    String user = auth.replace("Bearer ", "");
    OrganizerEvent event = organizerEvents.get(id);
    if (event == null || (!event.published() && !event.owner().equals(user))) {
        return ResponseEntity.status(404)
            .body(Map.of("error", "no such event"));
    }
    if (!event.owner().equals(user)) {
        return ResponseEntity.status(403)                    // I know you; you may not
            .body(Map.of("error", "only the owner may delete this event"));
    }
    organizerEvents.remove(id);
    return ResponseEntity.noContent().build();
}
```

The session (real captures):

```text
=== 401: no Authorization header at all ===
HTTP/1.1 401 
WWW-Authenticate: Bearer
{"error":"no credentials"}

=== 200: bob reads alice's PUBLISHED event ===
HTTP/1.1 200 
{"name":"Coldplay Live","owner":"alice","published":true}

=== 403: bob tries to DELETE alice's published event ===
HTTP/1.1 403 
{"error":"only the owner may delete this event"}

=== 404: alice reads bob's UNPUBLISHED event (it exists!) ===
HTTP/1.1 404 
{"error":"no such event"}

=== 200: bob reads his own unpublished event ===
{"name":"Secret Launch Party","owner":"bob","published":false}

=== 404: event 99 genuinely does not exist ===
HTTP/1.1 404 
{"error":"no such event"}
```

### Walkthrough

1. **The `401`:** no `Authorization` header — the server cannot say *who* is asking, so
   permissions are not even in play. `WWW-Authenticate: Bearer` is the server saying
   "present a bearer token and we can talk". Fix: supply credentials, retry.
2. **The `403`:** same bob who could *read* event 1 may not *delete* it. The server knows
   exactly who he is; he still may not. A fresh login changes nothing. Event 1 is
   published — existence is public — so admitting "it exists but you can't" leaks nothing.
3. **The two `404`s are the heart:** alice asking for event 2 (exists, but is bob's
   unpublished secret) and alice asking for event 99 (never existed) get *identical*
   status and body. Had the server answered `403` for event 2, alice would learn: *bob has
   a secret event with id 2* — on a ticketing platform, "a competitor is planning a
   launch", leaked by a status code.
4. The last `200` closes the loop: bob fetches event 2 fine, proving the earlier `404`
   was about *who asked*, not what exists.

### Where is it used?

- **Expired token** → `401` → client silently refreshes the token and retries (built in
  Phase 2); users never see it.
- **Valid customer, wrong powers** → `403` — a customer trying an organiser-only action.
- **Existence is the secret** → `404` — other organisers' unpublished events, other
  users' orders.

### Common mistakes

- **`401` for permission problems.** Clients react to `401` by re-authenticating — users
  get bounced to login, log in fine, retry, `401` again, forever. The loop is the
  signature of this bug.
- **`403` for things that should be hidden.** Attackers enumerate `/orders/1`, `/2`,
  `/3`…; `403` vs `404` tells them exactly which ids exist. Uniform `404` teaches nothing.
- **Treating `403` as "log in again".** Credentials are not the problem; a fresh token
  earns the same `403`.
- **Reading "Unauthorized" literally.** The phrase on `401` misleads; the meaning is
  unauthenticated. `403` is the one about authorisation.

### Summary

- `401`: unknown caller — get credentials, retry. `403`: known caller, forbidden action —
  credentials will not help.
- `403` admits the thing exists. When existence is the secret, answer `404` — identical
  to the real not-found.
- Hidden-`404` and genuine-`404` must be byte-for-byte indistinguishable, or the hiding
  fails.

### Practice questions

**Question 1.** A ticketing API gets three requests. Assign `401`, `403`, or `404` to
each, with a one-line reason:

1. `DELETE /events/7` arrives with no `Authorization` header at all. Event 7 exists and
   is published.
2. `DELETE /events/7` arrives from a correctly logged-in customer account — but event 7
   belongs to an organiser, and customers may never delete events. Event 7's existence is
   public.
3. `GET /events/8` arrives from a correctly logged-in organiser — but event 8 is a
   different organiser's unpublished draft.

**Answer.** (1) `401`: with no credentials at all, identity is unknown, so the permission
question cannot even be asked yet; the server should include `WWW-Authenticate` so the
client knows how to present credentials. (2) `403`: identity is settled and valid — the
problem is purely permission, and since event 7's existence is public knowledge, admitting
"it exists but you may not delete it" leaks nothing. (3) `404`: the draft's existence is
itself the secret; a `403` would confirm to a rival organiser that event 8 exists. Answer
exactly as if it did not.

**Question 2.** A security reviewer finds that `GET /orders/{id}` returns `403 Forbidden`
whenever the order belongs to a different customer, and `404 Not Found` when the id
matches no order. Explain the attack this enables — what can a patient attacker with a
plain logged-in account learn by walking through ids 1, 2, 3, …? Then state the fix and
what the attacker sees afterwards.

**Answer.** The attacker requests `/orders/1`, `/orders/2`, `/orders/3`, … and sorts the
answers: every `403` means "this order exists but belongs to someone else"; every `404`
means "no such order". Without ever seeing an order's contents, the attacker now has a
census — exactly which ids exist, so how many orders the business has taken, how fast ids
grow per day (sales volume), and a target list for any future bug that leaks contents.
The fix: return `404` for other customers' orders too, byte-for-byte identical to the
genuine not-found. Afterwards the attacker sees one uniform `404` wall — existing-but-
foreign and never-existed are indistinguishable, and the walk learns nothing.

---

## Lesson 23 — The server-side failures: 500, 502, 503, 504, and which to retry

### What is it?

The four `5xx` codes met constantly. `500 Internal Server Error`: the application itself
hit a bug. `502 Bad Gateway`: a middle box asked the backend and could not get a usable
answer. `503 Service Unavailable`: the server is alive but temporarily cannot serve.
`504 Gateway Timeout`: a middle box asked the backend and gave up waiting.

One idea unlocks all four: in production the app almost never faces the internet alone. A
**gateway** (nginx, a cloud load balancer) sits in front and forwards every request.
`500` and `503` are usually spoken *by the app*. `502` and `504` are spoken *by the
gateway, about* the app.

### Why do we need it?

"The server failed" is four situations, each telling the client and the on-call engineer
something different: code broken (`500`), app unreachable (`502`), alive but drowning
(`503`), alive but too slow (`504`). Wrong retry decisions are expensive: hammering a
drowning server worsens outages; retrying a non-idempotent request after a timeout
duplicates work.

### How does it work?

Follow a request through the two boxes. The gateway forwards to the app with a patience
limit, then:

- App throws an exception → app answers `500`, gateway relays it.
- App knows it cannot serve right now → app answers `503`, usually with `Retry-After`
  saying when to come back; gateway relays it.
- App is *down* — connection refused → the gateway manufactures a `502`.
- App is up but *silent past the patience limit* → the gateway manufactures a `504`.

Retry rules, code by code:

- **`500`: do not blind-retry.** A deterministic bug gives the same answer every time.
  Log it, alert a human.
- **`503`: retry politely.** Explicitly temporary. Obey `Retry-After` if present;
  otherwise wait increasingly long between attempts (**backoff**) so a thousand clients
  do not re-stampede a recovering server in the same second.
- **`502`: retry with backoff.** Usually a crashed or restarting app; often another
  instance answers next time.
- **`504`: the trap.** The lost-reply problem: either the work never happened, or it
  finished and only the reply was lost. The *gateway* stopped waiting; the app may still
  be finishing the work. Retry only if the request is idempotent (twice leaves the same
  state as once). Timed-out `GET`/`PUT`: retry freely. Timed-out `POST /orders`:
  retrying may create a second order.

### The demo

A real (tiny) gateway in front of the toy app: a Python server on port 9000 forwarding
every request to the app on port 8080, waiting at most 2 seconds — nginx's job in 40
lines. The app got two new endpoints:

```java
@GetMapping("/slow")
String slow() throws InterruptedException {
    Thread.sleep(10_000);                    // pretend a query takes 10 seconds
    return "finally done";
}

@GetMapping("/busy")
ResponseEntity<?> busy() {
    return ResponseEntity.status(503)        // temporarily cannot serve
        .header("Retry-After", "30")         // come back in 30 seconds
        .body(Map.of("error", "temporarily overloaded, try again shortly"));
}
```

The captures, in order (all real):

```text
=== 500: a bug inside the app (direct, port 8080) ===
HTTP/1.1 500 
{"timestamp":"2026-10-21T19:13:39.391+00:00","status":500,
 "error":"Internal Server Error","path":"/boom"}

=== 503: the app says it is temporarily unable (direct) ===
HTTP/1.1 503 
Retry-After: 30
{"error":"temporarily overloaded, try again shortly"}

=== 200 through the gateway (port 9000): backend healthy ===
HTTP/1.0 200 OK
{"message":"hello","id":1}

=== 504: backend needs 10s, gateway waits only 2s ===
HTTP/1.0 504 Gateway Timeout
{"error":"backend took too long"}        <- curl measured: 2.015 seconds total

=== 502: backend is DOWN, gateway cannot reach it ===
HTTP/1.0 502 Bad Gateway
{"error":"could not reach backend"}
```

(The gateway lines say `HTTP/1.0` because Python's basic server speaks the older version
by default; the status codes work identically.)

### Walkthrough

1. **`500`:** `/boom` throws a `RuntimeException`; Spring's safety net wrote the default
   error JSON. The request was flawless — the fault is in the code, and an identical
   retry hits the identical bug.
2. **`503`:** the app is healthy enough to *say* it cannot serve. `Retry-After: 30` turns
   the retry question into an instruction: not never, not now — in 30 seconds.
3. **The healthy `200` through port 9000** proves the gateway relays faithfully when all
   is well.
4. **`504`:** `/slow` needs 10 seconds; gateway patience is 2. The answer arrived in
   2.015 seconds — *before the app finished*. The `504` was manufactured by the gateway;
   the app's handler was mid-sleep and would have completed its work. The lost-reply trap
   in the flesh.
5. **`502`:** the app was killed, then the gateway asked again. Connection refused → the
   gateway manufactured `502`. The app said nothing; it could not — it does not exist
   right now.

### Where is it used?

Unhandled `NullPointerException` in order code → `500`, page someone. App restarting
during a deploy → `502` from the load balancer for 20 seconds. On-sale day overload →
`503` with `Retry-After`. A seat-map query taking 31 seconds against a 30-second balancer
limit → `504`.

### Common mistakes

- **Retrying `500` in a tight loop.** Same bug, same answer, plus self-inflicted load.
- **Instant synchronized retries of `503`.** Everyone retrying at once is a retry storm;
  it turns a wobble into an outage. Backoff exists for this.
- **Retrying a non-idempotent request after `504`.** The gateway gave up; the app may
  have finished. Double orders, double charges. This is why `Idempotency-Key` exists
  (F4.4).
- **Blaming the app for every `502`/`504`.** Those are the *gateway's* words. The app
  might be down or slow — or the gateway might be pointing at the wrong port. Check which
  box spoke before debugging.

### Summary

- `500`/`503`: the app speaks. `502`/`504`: the gateway speaks, about the app.
- Retry: `500` no (bug repeats); `503` yes, obeying `Retry-After`; `502` yes, with
  backoff; `504` only if the request is idempotent — the work may have completed.
- A `504` means "no answer reached the gateway", not "nothing happened".

### Practice questions

**Question 1.** A ticketing system has a load balancer (patience: 30 seconds) in front of
the app. For each situation, say which box speaks — the app or the load balancer — and
which code it says: (a) the app's order-saving code throws an unhandled exception; (b)
the app is mid-restart during a deploy, so connections to it are refused; (c) the app
answers health checks fine but a seat-map query takes 45 seconds under load; (d) the app
detects it is overloaded and wants everyone to come back in a minute.

**Answer.** (a) The app speaks: `500` — its own code failed, and the balancer just relays
the answer. (b) The load balancer speaks: `502` — it cannot connect to the app at all, so
it manufactures the answer itself; the app says nothing because it cannot. (c) The load
balancer speaks: `504` — the app is alive and still working on the query, but 45 seconds
exceeds the balancer's 30-second patience, so the balancer gives up and answers for it.
(d) The app speaks: `503`, ideally with `Retry-After: 60` — it is alive enough to declare
itself temporarily unable.

**Question 2.** A payment client sends `POST /payments` (creates a new payment) and
receives `504 Gateway Timeout` after 30 seconds. A teammate says: "It timed out, so
nothing happened — just send it again." Name the two possible worlds the client is
actually in, explain which box produced the 504 and why that matters, and say what
property the request would need before "just send it again" is safe.

**Answer.** The two worlds: (1) the payment request never reached the app, or failed
before any work — nothing happened; (2) the app received it and completed the payment,
but took longer than the gateway's patience, so the reply never reached the client. The
`504` was produced by the *gateway*, not the app — that matters because the gateway gave
up waiting while the app may have continued working to completion; "timed out" describes
the gateway's patience, not the app's progress. The teammate's "nothing happened" simply
is not knowable. Resending is only safe if the request is idempotent — doing it twice
leaves the same state as doing it once. A bare `POST /payments` is not idempotent (each
send can create a new payment), so blind resending risks a double charge. It becomes safe
once the request carries something that lets the server recognise a repeat — an
`Idempotency-Key` header (coming in F4.4) — or is restructured as an idempotent operation.

---

## Lesson 24 — The lie that breaks everything: 200 with an error in the body

### What is it?

The most tempting beginner shortcut in API design: handle a failure by returning status
`200 OK` with something like `{"error":"not found"}` in the body. The status line says
*success*; the body whispers *failure*. One habit, and half the machinery of the web is
quietly disabled.

### Why do we need it?

An HTTP response talks to two audiences at once. The **body** is for whoever ultimately
reads the data. The **status line** is for *machines in the middle*: scripts, retry
logic, caches, load balancers, monitoring dashboards. None of those middle machines read
the body — they cannot; every API's body is shaped differently. The status code is the
one field they all understand — a fixed three-digit number in a fixed position. Put the
error only in the body, and every machine in the chain hears "all good."

### How does it work?

The status line is the machine-readable channel; the body is the human-readable channel.
Tools are built on one assumption: *the status line tells the truth*.

- A client script branches on the status: `2xx` → use the body as data; `4xx`/`5xx` →
  error path.
- A retry policy fires on `5xx` and certain others — it never inspects bodies.
- A cache may store a `200` answer to a `GET` and serve it to the next caller.
- A monitoring dashboard computes error rate as the percentage of responses with status
  ≥ 400.

Lie on the status line and each of these does exactly what it was told — on false
information.

### The demo — break it on purpose, then change it back

The honest lookup added to the toy app:

```java
@GetMapping("/events/{name}")
ResponseEntity<?> getEvent(@PathVariable String name) {
    for (ToyEvent e : events) {
        if (e.name().equals(name)) {
            return ResponseEntity.ok(e);
        }
    }
    return ResponseEntity.status(404)                       // honest: error on the
        .body(Map.of("error", "no event named " + name));   // status line itself
}
```

Honest run (`curl -f` makes curl fail on 4xx/5xx — how shell scripts branch; the
"monitor" fires five requests and tallies status codes, which is all a dashboard does):

```text
=== HONEST VERSION ===
--- missing event ---
HTTP/1.1 404 
{"error":"no event named ghost"}
--- a script that branches on the status ---
script says: FAILURE (curl exit 22)
--- what a monitor sees: 5 requests for the missing event ---
   5 404
```

The one-line sabotage — `.status(404)` became `.status(200)` — and the same probes:

```text
=== LYING VERSION (same request as before) ===
--- missing event ---
HTTP/1.1 200 
{"error":"no event named ghost"}
--- the same script, branching on the status ---
script says: SUCCESS, got {"error":"no event named ghost"}
--- the same monitor: 5 requests for the missing event ---
   5 200
```

Then changed back and re-verified: `HTTP/1.1 404` once more, as the task instructs.

### Walkthrough — the chain of victims

1. **The script treats an error as data.** It printed `SUCCESS, got {"error":...}`. Real
   code would call something like `order.getId()` on an error message next — a crash, or
   garbage flowing onward.
2. **The monitor goes blind.** Honest: five `404`s, the error-rate graph rises, on-call
   gets curious. Lying: five clean `200`s — *the error rate graph stays flat while users
   are stuck*. The outage is invisible to the people paid to notice it.
3. **Retry policies stay asleep.** Infrastructure that retries on `5xx` sees `200` and
   delivers the failure as a final, successful answer.
4. **Caches can store the lie.** A `200` on a `GET` is a candidate for caching. If the
   error is cached for five minutes, the event can be created a second later and callers
   keep receiving the *cached* "no event named ghost" — a stale lie served at high speed.

(Subtlety: `404` is itself cacheable in some setups, but caches treat error responses far
more carefully and briefly. Every middle machine made the correct decision *for the
status it was shown*.)

### Where is it used?

The rule — errors travel on the status line — is what makes every earlier code usable:
`401` triggering token refresh, `409` triggering a state refetch, `503` triggering polite
backoff, `5xx` triggering alerts. All of it works only because clients can trust the
line. APIs that lie with `200` force every client into custom body-sniffing, different
for every endpoint — and middle boxes can never be taught at all.

### Common mistakes

- **"My error is in the body, clients can check."** *Your* client maybe. The cache, the
  retry layer, the dashboard, and every future client that trusted your status line —
  none of them check.
- **Wrapping everything in `200` + `{"success": true/false}`.** The same lie in a uniform
  costume; all four victims still apply.
- **Lying only "for small errors".** A client cannot know which endpoints tell the truth;
  one lying endpoint poisons trust in all of them.
- **The reverse lie.** A correct error status with a success-looking body confuses humans
  instead of machines. Both channels should agree.

### Summary

- The status line is for machines; the body is for the final reader. Errors must travel
  on the machine channel.
- `200` + error body breaks four things at once: client branching, retry policies,
  caches, and monitoring.
- One digit changed in the demo; the script reported success on a failure and the monitor
  flatlined.

### Practice questions

**Question 1 — the eight-scenario exercise from the task.** Assign one status code to
each ticketing scenario, with a one-line reason. One new code appears: `429 Too Many
Requests` — a `4xx` meaning "you, specifically, are sending too much; slow down", often
with `Retry-After`.

1. A customer tries to hold seat H7, but another customer's hold on H7 is active.
2. `GET /events/123456` and no event with that id has ever existed.
3. A request body arrives as `{"name": "Gig",}` — trailing comma.
4. A request to create a ticket priced at minus ten.
5. A request arrives with no `Authorization` header on an endpoint that requires login.
6. A correctly logged-in customer tries to publish another organiser's event (the
   event's existence is public).
7. One client is sending three hundred requests a second.
8. Your app calls its payment provider while handling a request, and the provider times
   out.

**Answer.**

1. `409 Conflict` — the request is flawless; it collided with current state (another
   hold), a fault of timing that could succeed later.
2. `404 Not Found` — nothing at that identifier; a genuine not-found.
3. `400 Bad Request` — the trailing comma makes the body unparseable; the failure is in
   the parser, before any application logic.
4. `422 Unprocessable Entity` — parses fine, but a negative price is invalid on its own
   terms, regardless of state, forever.
5. `401 Unauthorized` — identity unknown; send `WWW-Authenticate` so the client knows
   how to present credentials. (Read it as *unauthenticated*.)
6. `403 Forbidden` — identity known and valid; permission is the problem, and since the
   event's existence is public, admitting it exists leaks nothing. (Were it an
   unpublished draft, `404` to hide its existence.)
7. `429 Too Many Requests` — a `4xx` because the fault is the sender's behaviour; add
   `Retry-After` to say when to resume.
8. `504 Gateway Timeout` — relative to the payment provider, your app is the gateway:
   it waited, the upstream took too long. Not `500` — your code did not fail. (If the
   provider had answered with something unusable rather than timing out, `502`.)

**Question 2.** An on-call engineer watches a dashboard whose error rate is "percentage
of responses with status 400 or above". A teammate changes the order-lookup endpoint so a
missing order returns `200` with `{"error":"order not found"}` instead of `404`, arguing
"the mobile app checks the body anyway". Describe what the dashboard shows during the
next incident where a bug makes *every* order lookup come back "not found", what the
automatic retry layer (configured to retry on `5xx`) does, and what happens to any cache
that stores `200` responses to `GET` requests for sixty seconds.

**Answer.** During the incident, every lookup fails — and the dashboard shows a 0% error
rate, a perfectly flat graph, because every response is a `200`. No alert fires; the
on-call engineer learns about the outage from angry users, not from the monitoring that
exists precisely for this. The retry layer does nothing: it watches for `5xx`, sees only
`200`, and delivers each failure as a final successful answer — even failures a retry
might have papered over. The cache makes it worse twice over: it stores
`{"error":"order not found"}` as a valid `200` answer for sixty seconds, so (a) during
the incident it serves the error at high speed without even asking the app, and (b) for
up to a minute *after the bug is fixed*, callers still receive the cached lie — the
outage outlives its own fix. The teammate's argument covers only the mobile app — the one
machine in the chain that does read the body — and ignores every other machine that
cannot.

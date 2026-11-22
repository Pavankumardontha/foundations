# Task F4.5 — Designing a REST resource

## Lesson 31 — REST: an API made of things, not function calls

### What is it?

**REST** is a style for designing APIs built on one idea: expose *things* at URLs, and
act on those things with the HTTP methods you already know. A **resource** is any thing
your API exposes — events, venues, seat maps, seats, holds, orders, payments, tickets.
Each resource lives at a URL in one of two shapes: a **collection**, representing many of
something (`/orders`), and an **item**, representing exactly one (`/orders/1`).

### Why do we need it?

The alternative is an API shaped like a pile of function calls — `/createOrder`,
`/getOrderById`, `/cancelOrderNow` — where every operation needs its own invented name
and a newcomer must read documentation to learn each one. REST replaces invented names
with a grid: a handful of nouns down one side, the five methods across the top. Know the
noun `/orders` and you can *predict* the rest. The API is the most visible thing this
project produces, and once other software depends on a path, changing it is a migration,
not an edit. A predictable grid is the cheapest contract to keep.

### How does it work?

Three rules build the whole style:

1. **Name things, not actions.** The URL contains *what* (a noun); the method says *what
   to do* (the verb). `POST /events` — the verb lives in `POST`, not in the path.
2. **Collections and items pair up.** `/events` is the set; `/events/{id}` is one member.
   `GET` on a collection lists, on an item fetches; `DELETE` on an item removes that one
   thing.
3. **One noun, many operations.** New operations on the same thing get new methods on the
   same URL, not new URLs. The noun count stays small; the grid does the multiplying.

### The demo

One noun — `orders` — exercised live against the toy app:

```text
=== 1) the collection, before anything exists ===
GET /orders
HTTP/1.1 200 
[]

=== 2) POST to the collection: add one to the set ===
POST /orders   {"item":"2 tickets, Coldplay"}
HTTP/1.1 201 
Location: /orders/1

=== 3) the collection again: now it holds one ===
GET /orders
[{"id":1,"item":"2 tickets, Coldplay"}]

=== 4) an ITEM: one specific thing at its own address ===
GET /events/gig
HTTP/1.1 200 
{"name":"gig"}
```

### Walkthrough

1. **A collection is a real thing even when empty.** `GET /orders` before any order
   exists answers `200` with `[]` — the set exists and has no members. (This becomes a
   design decision with teeth later: empty collection versus missing item.)
2. **Creation is `POST` *to the collection*.** No `/createOrder`; a new member is posted
   to the set it belongs to, and the `201` + `Location: /orders/1` shows the server
   assigned the new item its address inside the collection.
3. **The collection reflects the change.** Same URL, different contents — the noun stayed
   put while its state moved.
4. **The item shape:** collection name, then identifier — `/events/gig`, "the event
   called gig". Every item's address embeds the collection it belongs to.

The grid, explicitly — one noun, everything you can say about it:

| Method + URL | Meaning |
| --- | --- |
| `GET /orders` | list the orders |
| `POST /orders` | create a new order in the set |
| `GET /orders/1` | fetch order 1 |
| `PUT` / `PATCH /orders/1` | replace / amend order 1 |
| `DELETE /orders/1` | remove order 1 |

Five operations, zero invented names. Safety, idempotency and the retry rules plug
straight into this grid, because the grid is made of the same methods.

### Where is it used?

Stripe exposes `/charges` and `/refunds`; GitHub exposes `/repos` and `/issues` — nouns
and methods all the way down. F4.5's deliverable is the paper design: events, venues,
seat maps, holds, orders as nouns, each with collection and item URLs, a method grid, and
a status code for every outcome.

### Common mistakes

- **Verbs in URLs.** `/getEvents`, `/createOrder` — the method already says it; the path
  saying it again is how APIs rot into function piles.
- **Breaking the collection/item pairing.** `/order?id=5` as the only way to reach one
  order buries identity in a parameter and makes `Location: /orders/5` impossible.
- **A new URL per operation.** `/orders/1/delete` as a path — the delete already has a
  home: `DELETE /orders/1`.
- **Nouns that mirror database tables.** Resources are what *clients* think in — an
  order, a ticket — not what storage calls things. The API is a contract with outsiders,
  not a window into the schema.

### Summary

- REST = nouns at URLs × standard methods; the verb lives in the method.
- Collection (`/orders`) and item (`/orders/1`) pair up; creation is `POST` to the
  collection; the item's address arrives in `Location`.
- Small noun set, predictable grid, stable contract.

### Practice questions

**Question 1.** A teammate designing a ticketing API proposes these URLs: `/orders` (all
orders), `/orders/{id}` (one order), and additionally `/orderList`, `/orderDetails`, and
`/newOrder`. Using the idea that an API should be a small set of nouns multiplied by
standard methods, say which of the five URLs should survive, what method replaces each of
the three rejected ones, and what a client gains from the smaller design.

**Answer.** Survivors: `/orders` and `/orders/{id}` — one collection, one item, and
together they cover everything. The rejected three are operations wearing URL costumes:
`/orderList` is `GET /orders`; `/orderDetails` is `GET /orders/{id}`; `/newOrder` is
`POST /orders`. The client gains predictability: knowing the single noun `orders`, every
operation is guessable from the standard methods — nothing to memorise, less
documentation to read, and the pieces compose (the `201` from `POST /orders` can hand out
`Location: /orders/812`, which is a real, fetchable URL in the same grid). Five URLs
shrink to two with no loss of capability.

**Question 2.** In a REST API for a library, you need to support: listing all books,
fetching the book with id 12, adding a new book, and removing the book with id 12. Write
the four method-plus-URL pairs, and state which URL is a collection and which is an item.

**Answer.** Listing: `GET /books`. Fetching one: `GET /books/12`. Adding: `POST /books`
(the new book is posted to the set it joins; the response should be `201` with
`Location: /books/<new id>`). Removing: `DELETE /books/12`. `/books` is the collection —
it represents many books; `/books/12` is the item — exactly one book, addressed by the
collection name plus an identifier. Two URLs, four operations, no invented names.

---

## Lesson 32 — Path parameters vs query parameters: identifying versus filtering

### What is it?

Two places a URL carries variable information, with two different jobs. A **path
parameter** is a variable piece *inside* the path — the `gig` in `/events/gig`, the `12`
in `/orders/12`. It identifies *which thing*. A **query parameter** is a `?name=value`
pair *after* the path — the `q=g` in `/events?q=g`. It refines *how you want the set*:
filtering, sorting, paging. One names a thing; the other narrows a list.

### Why do we need it?

"Give me order 12" and "give me orders matching these conditions" are different kinds of
question. The path is the resource's *address* — what a `Location` header points at, what
you bookmark, what `404` is about. Filters are *optional refinements* — a collection URL
must mean something with zero, one, or five of them attached. Identity in a query
parameter (`/order?id=12`) denies the thing an address; a filter in the path
(`/events/leeds-only`) mints a fake resource per filter combination.

### How does it work?

One question decides: **does removing it change *which thing*, or just *how much of the
set*?**

- Remove `12` from `/orders/12` → no longer that order. Identity. Path.
- Remove `?city=Leeds` from `/events?city=Leeds` → still a meaningful request, the same
  collection unfiltered. Refinement. Query.

Query parameters stack with `&`, each optional:
`/events?city=Leeds&sort=startsAt,desc&limit=20` — filter, sort, page, all decorating the
*same* collection. The answer shapes differ too: an item URL returns *the object*; a
filtered collection returns *a list*, even when one thing — or nothing — matches.

### The demo

```java
@GetMapping("/events")
List<ToyEvent> listEvents(@RequestParam(value = "q", required = false) String q) {
    if (q == null) {
        return events;                                   // no filter: whole set
    }
    return events.stream()
        .filter(e -> e.name().contains(q))               // narrowed set
        .toList();
}
```

(`@RequestParam` is Spring's "hand me a query parameter", as `@PathVariable` is its "hand
me a piece of the path". Full annotation lesson in F4.7.)

Three events exist: gig, gala, opera.

```text
=== 1) the whole collection ===
GET /events
[{"name":"gig"},{"name":"gala"},{"name":"opera"}]

=== 2) query parameter: filter the set (?q=g) ===
GET /events?q=g
[{"name":"gig"},{"name":"gala"}]

=== 3) path parameter: identify ONE thing ===
GET /events/gig
{"name":"gig"}

=== 4) same word, both positions: ?q=gig vs /gig ===
filter  ?q=gig  -> [{"name":"gig"}]
identity /gig    -> {"name":"gig"}
```

### Walkthrough

1. **Captures 1–2: one resource, narrowed.** `/events` and `/events?q=g` are the same
   collection; the filter removed `opera` (no letter g) without changing what the
   resource *is*.
2. **Capture 3: the path piece picks a member** — `/events/gig` means *that event*, a
   thing with its own address.
3. **Capture 4 is the whole lesson in two lines.** The same word in two positions gives
   different *shapes*: the filter answers with a **one-element list** `[{"name":"gig"}]`,
   brackets and all; the path answers with **the object** `{"name":"gig"}`. A filter
   result is always a set; an identity result is always the thing. Clients feel it
   immediately: one you loop over, the other you use directly.
4. Preview of a neighbouring rule: nothing matches a *filter* → `200` + empty list (the
   collection exists); a missing *identity* → `404` (the thing is not there).

### Where is it used?

The task's own split for the platform: identity in paths (`/events/{id}`,
`/orders/{id}`); listing events via query parameters — filtering (`city`, `from`, `to`,
`q`), sorting (`sort=startsAt,desc`), paging (`limit`, `offset`). Stripe reads the same
way: `/charges?customer=cus_123&limit=10`.

### Common mistakes

- **Identity in a query parameter.** `/order?id=5` denies the order an address;
  `Location` has nothing clean to point at; the collection/item pairing dissolves.
- **Filters in the path.** `/events/leeds` — event id or city filter? Ambiguity now,
  migration later. Filters are optional; the path is not the place for optional things.
- **Required query parameters on a collection.** If bare `GET /events` is an error, the
  collection URL has stopped meaning anything.
- **Collapsing a one-match filter result to a bare object.** The one-element list is
  correct; shape must not depend on data, or parsers break the day a filter matches
  twice.

### Summary

- Path = which thing (identity, address). Query = how much of the set (filter, sort,
  page) — never identity.
- Decision test: removing it changes *which thing* → path; changes *how much* → query.
- Item answers are objects; collection answers are lists, even with one or zero matches.

### Practice questions

**Question 1.** A ticketing API needs these four requests. For each, decide whether the
variable part belongs in the path or in a query parameter, and write the URL: (a) fetch
the event whose id is 42; (b) list events happening in Leeds; (c) list events sorted by
start time, newest first; (d) delete the hold whose id is `h-991`.

**Answer.** (a) Path — the 42 says *which* event: `GET /events/42`. (b) Query — Leeds
narrows the set; the collection is the resource: `GET /events?city=Leeds`. (c) Query —
sorting changes how the set comes back, not which set:
`GET /events?sort=startsAt,desc`. (d) Path — `h-991` identifies the one hold being
removed: `DELETE /holds/h-991`. Rule of thumb applied four times: identity in the path,
refinement after the question mark.

**Question 2.** A teammate's API exposes exactly one way to fetch a single order:
`GET /orders?id=812`, returning a one-element list `[{"id":812,...}]`. Give two concrete
problems this causes — one involving the `Location` header on order creation, and one
involving the shape of the response a client must parse — and write the design that fixes
both.

**Answer.** Problem one: when `POST /orders` creates an order, the `201` should carry
`Location` pointing at the new order's address — but in this design the order *has* no
address; the best available value is `Location: /orders?id=812`, a filtered-collection
URL rather than an item, and clients following it get a list, not the thing. Problem two:
every client fetching one order receives a list it must unwrap — check non-empty, take
element zero, decide what two elements would even mean — for a lookup that can only ever
have one answer; the set shape is wrong for an identity question. The fix is the standard
pairing: `GET /orders/812` returns the bare object `{"id":812,...}` (and `404` when
absent), `POST /orders` answers `201` with `Location: /orders/812`, and `/orders?...`
remains for genuine list questions — filters, sorting, paging.

---

## Lesson 33 — Nouns, not verbs: rewriting the five badly named endpoints

### What is it?

The naming rule that makes or breaks a REST API: **nouns in the path, verbs in the
method**. `/createEvent` smuggles the verb into the name; `POST /events` puts each part
where it belongs — the method says *do what*, the path says *to what*.

### Why do we need it?

HTTP already *has* verbs, with meanings every client, cache, and retry layer understands:
`GET` is safe to prefetch, `PUT` and `DELETE` are safe to retry, `POST` is neither. A
verb-named URL discards that machinery — nothing knows `POST /cancelOrderNow` is
delete-like or that `GET /getEventById` is really a read. And verb-names breed:
`/createEvent`, `/createEventDraft`, `/createEventAndPublish` — a vocabulary nobody can
predict. Nouns stay countable; methods are already standardized.

### How does it work?

The rewrite procedure, mechanical in four steps:

1. **Find the noun** buried in the name: `createEvent` → *event*.
2. **Find the verb** and map it: create → `POST`, read → `GET`, full update → `PUT`,
   partial update → `PATCH`, remove → `DELETE`.
3. **Place the noun** as collection or item: creating joins a set → `POST /events`;
   acting on one thing → item path, identity *in the path*.
4. **Delete the verb words from the URL.** If `get`, `create`, `update`, `list`,
   `cancel`, or `now` survive in the path, you are not done.

The payoff, live — ask the `/events` collection for a method it does not support, and the
noun *advertises its own verbs*:

```text
=== DELETE on the /events collection ===
HTTP/1.1 405 
Allow: GET, POST

=== and a verb-named URL simply has no home ===
POST /createEvent
HTTP/1.1 404 
```

`405` + `Allow: GET, POST` is the grid made machine-readable — "this noun answers to
these verbs." A `/createEvent` URL could never say that; it is one cell pretending to be
a whole table.

### The five rewrites

| Before | After | What changed |
| --- | --- | --- |
| `POST /createEvent` | `POST /events` | *Create* moved into `POST`; the path became the collection the new event joins. |
| `GET /getEventById?id=123` | `GET /events/123` | Two sins fixed: *get* moved into the method, and identity moved from query parameter into the path — the event gains a real address. |
| `POST /updateVenue` | `PUT /venues/7` (full replace) or `PATCH /venues/7` (partial) | *Update* becomes a method — and the rewrite forces a hidden question: *which* venue? The identity was missing entirely. |
| `GET /listAllSeatsForEvent?event=5` | `GET /events/5/seats` | *ListAllFor* dissolves: listing is `GET` on a collection; "for event 5" is the seats collection placed under its owner. |
| `POST /cancelOrderNow` | `DELETE /orders/123` | *Cancel* maps to `DELETE` on the one order — buying idempotency: a retried cancel is harmless, where a retried `POST /cancelOrderNow` was anyone's guess. |

### Walkthrough — the three instructive ones

1. **`/getEventById?id=123` was wrong twice.** The verb is the visible sin; the hidden
   one is identity in a query parameter — event 123 had no address. After the rewrite,
   `/events/123` is bookmarkable, `Location`-able, and `404`-able.
2. **`/updateVenue` hid a missing decision.** Rewriting forced two questions the old name
   papered over: *which venue*, and *what kind of update* — replace everything (`PUT`,
   omitted fields erased) or change some fields (`PATCH`). The old name could not express
   the difference.
3. **`/cancelOrderNow` is the honest hard case.** When cancelling simply ends the order,
   `DELETE /orders/123` is clean and idempotent. But real cancellation often *does
   things* — refunds, a persistent `cancelled` status. When an operation is genuinely
   more than remove-the-thing, REST has a controlled pattern: an action scoped under a
   noun (`POST /events/42/publish`), taught at the end of this task. For the rewrite,
   `DELETE` is the defensible answer; the action pattern is the fallback when the
   business process demands more.

Every rewrite shares a property: the leftover path is a thing you could also `GET`. That
is the self-test — a URL that only makes sense with one particular verb attached is
probably a verb in disguise.

### Where is it used?

F4.5's first subtask, verbatim — and the single most common API review comment in real
teams. Stripe cancels a subscription with `DELETE /subscriptions/{id}`, not
`/cancelSubscriptionNow`.

### Common mistakes

- **Half-rewrites.** `POST /events/create` — the noun arrived but the verb stayed.
- **Rewriting the URL but not the method.** `POST /events/123` as a read keeps the grid
  broken — reads must be `GET` to gain safety, caching, prefetchability.
- **Letting "Now"/"All"/"ById" survive.** Instructions to the old function, not parts of
  the noun.
- **Treating every action as deletable.** `cancel` → `DELETE` works when cancel means
  *end it*; forcing stateful processes (publish, refund, pay) into `DELETE`/`PUT`
  contortions is the opposite error — the action pattern exists for those.

### Summary

- Nouns in the path, verbs in the method; the rewrite is mechanical: find noun, map verb,
  place as collection/item, delete verb words.
- Verb URLs discard HTTP's machinery (safety, idempotency, caching) and breed
  unpredictable vocabularies.
- `405` + `Allow` is the noun advertising its verbs — only possible when URLs are nouns.

### Practice questions

**Question 1.** Rewrite these three endpoints from a cinema API, naming the method and
path for each, and state the extra flaw in the second one beyond the verb in its name:
(a) `POST /addScreening`; (b) `GET /getTicketById?ticket=88`; (c)
`POST /removeScreeningNow`.

**Answer.** (a) `POST /screenings` — *add* is the method; the path is the collection the
new screening joins. (b) `GET /tickets/88` — *get* was already the method; the extra flaw
is identity hiding in a query parameter: ticket 88 had no address of its own, so nothing
could point at it (`Location`, bookmarks, a clean `404`). The id belongs in the path. (c)
`DELETE /screenings/{id}` — *remove* maps to `DELETE`, *Now* dies, and the rewrite
exposes that the old name never said *which* screening; the identity has to be supplied
in the path. As a bonus, the retried `DELETE` is harmlessly idempotent where a retried
`POST /removeScreeningNow` was unknowable.

**Question 2.** After a rewrite, a cinema API has exactly two URLs for screenings:
`/screenings` and `/screenings/{id}`. A client sends `PATCH /screenings` (the collection,
no id) and receives `405 Method Not Allowed` with `Allow: GET, POST`. Explain what the
server is telling the client, why `PATCH` makes no sense on a collection URL in this
design, and what request the client probably meant to send.

**Answer.** The server is saying: "the URL `/screenings` exists — this is not a `404` —
but it does not answer to `PATCH`; the verbs it understands are `GET` (list the
screenings) and `POST` (add one)." `PATCH` means "change some fields of this resource",
which makes sense for *one* screening — its time, its screen number — but not for the
collection as a whole: "partially update the set of all screenings" names no coherent
operation, so the collection simply does not offer it. The client almost certainly meant
to amend one particular screening and dropped the identity from the path:
`PATCH /screenings/{id}` with a body containing the fields to change.

---

## Lesson 34 — Nothing matched versus nothing there: 200 with [] versus 404

### What is it?

The design rule for empty-handed answers, hinging on the collection/item split. A
*collection* query matching nothing — `GET /events?city=Leeds` with no Leeds events —
answers **`200` with an empty list `[]`**: the collection exists and is simply empty. An
*item* request for something that does not exist — `GET /events/{id}`, unknown id —
answers **`404 Not Found`**: the specific thing addressed is not there.

### Why do we need it?

"No results" and "no such thing" are different answers to different questions, and
clients build different code paths on them. A search page with zero results is a *working
page*; a link to a deleted event is a *dead link*. Collapse the two and every client must
untangle them again, badly, in its own way.

### How does it work?

Match the answer to the question's shape:

- **A filter asks "which members satisfy this?"** The empty set is a valid answer —
  `200`, body `[]`. No error happened.
- **An identity asks "give me *this one thing*."** Not there → `404`; the address names
  nothing.

Question-mark parts narrow a set (empty set possible, still `200`); path parts name a
thing (missing thing is `404`).

### The demo

```text
=== 1) a brand-new system: the events collection, nothing in it yet ===
GET /events
HTTP/1.1 200 
[]

=== 2) a filter that matches nothing (?q=zzz) ===
GET /events?q=zzz
HTTP/1.1 200 
[]

=== 3) an identity that does not exist (/events/zzz) ===
GET /events/zzz
HTTP/1.1 404 
{"error":"no event named zzz"}

=== 4) what a client script does with each ===
/events?q=zzz -> render the list page: []
/events/zzz  -> show the error page (curl exit 22)
```

### Walkthrough

1. **An empty collection is healthy.** The day before the first event is created,
   `GET /events` *should* say `200 []` — the system works; there are no events yet. A
   `404` would claim the events feature itself is missing.
2. **A no-match filter is the same answer.** Zero is a count, not a failure.
3. **The missing identity is a genuine `404`** — the address names nothing.
4. **Capture 4 is the reward.** The empty list flowed into "render the list page" (an
   empty array loops zero times — no special case); the `404` flowed into the error
   branch (a dead-link page). Right code paths, zero detection logic in the client.

The awkwardness when confused — the task's "write down why":

- **`404` for an empty collection:** the client needs a special case — "if 404, pretend
  empty list" — because `404` now sometimes means *broken link*, sometimes *zero rows*. A
  mistyped URL (a real bug) and a legitimate empty result arrive identical. Monitoring
  shares the confusion: a burst of `404`s — broken links or unpopular searches? The
  dashboard cannot tell.
- **`200` with `null`/`{}` for a missing item:** the *success* path receives a non-thing
  and crashes a line later — the lying-`200` problem in a new costume.

### Where is it used?

Every list-plus-detail UI: search pages, "my orders", admin tables — list endpoints
return `200 []` and render zero rows gracefully; detail endpoints return `404` and route
to the not-found view. The paper design assigns these codes explicitly to every listing
and item fetch.

### Common mistakes

- **`404` when a filter matches nothing** — poisons `404` as a signal, forces client
  special cases.
- **`404` for an empty but existing collection** — the launch-day case.
- **`200` with `null` or `{}` for a missing item** — the success path receives nothing
  usable.
- **The boundary case:** `GET /events/5/seats` when *event 5 itself* does not exist. The
  parent identity failed, so `404` — resolve the path's identities first; only then does
  empty-set-is-fine apply to what the query narrows.

### Summary

- Filter matched nothing → `200 []`. Identity missing → `404`.
- Empty is a count; missing is a failure. Clients get the right code path for free when
  the API keeps them apart.
- Resolve path identities first; the empty-set rule applies only after they resolve.

### Practice questions

**Question 1.** A ticketing API receives three requests on launch day, before any event
has been created: (a) `GET /events`; (b) `GET /events?city=Leeds`; (c) `GET /events/1`.
Give the status and body shape for each, and the one-sentence reason.

**Answer.** (a) `200` with `[]` — the events collection exists and is empty; an empty
system is a healthy system. (b) `200` with `[]` — the filter narrowed an existing (empty)
collection to zero members; the question "which events are in Leeds?" was answered:
none. (c) `404` with an error body — `/events/1` addresses one specific event, and no
event 1 exists; the address names nothing, which is exactly what `404` is for.

**Question 2.** A teammate's events API returns `404 Not Found` whenever a search like
`GET /events?q=jazzfest` matches nothing, arguing "nothing was found, so Not Found is
accurate." Describe the two different situations a client of this API now cannot tell
apart, and give a concrete example of a real bug that would hide inside the ambiguity.

**Answer.** The client cannot distinguish (1) a healthy empty result — the search worked
and no event matches "jazzfest" — from (2) a genuinely broken request — a URL that names
nothing, such as a mistyped path or a route that does not exist. Both arrive as `404`.
Concrete hidden bug: a frontend developer ships a typo in the path — say
`GET /event?q=jazzfest` (singular) — which the server answers with a routing `404`. The
app's code, trained that `404` means "no results", renders a friendly "no events found"
page. Testers see a working search with zero hits instead of a crash; the typo sails
through review and ships, and every search in production silently returns "no results"
forever. With the correct design, the healthy empty search is `200 []` (renders zero
rows) and the typo's `404` lands in the error path, where it is caught the first time
anyone runs the page.

---

## Lesson 35 — Nesting: saying "belongs to" in a path, and knowing when to stop

### What is it?

**Nesting** is placing one resource's URL underneath another's to express ownership:
`/events/5/seats` reads as "the seats *belonging to* event 5". The nested part is still a
normal collection. Nesting is the last tool for shaping paths — with a hard limit:
**never go beyond two levels**.

### Why do we need it?

Some resources make no sense alone: a seat map is *an event's* seat map; asking for seats
without saying which event is an incomplete question. Nesting puts the required context
into the address. But every level is a cost: it hard-wires the ownership chain into every
URL, forcing clients to know and type the whole ancestry even when a single identifier
would do.

### How does it work?

1. **Nest for genuine "belongs to".** Child meaningless without parent → one level earns
   its place: `/events/{id}/seats`.
2. **Resolve the parent first.** `/events/zzz/seats` with no event `zzz` → `404`, *not*
   an empty seat list. All path identities resolve before any collection logic.
3. **Stop at two levels.** A long ancestry — `/orgs/{o}/events/{e}/sections/{s}/rows/{r}/seats/{x}`
   — means: flatten. Give the deep thing a top-level collection, reach it by query
   parameters: `GET /seats?seat-map-id=...&row=H`. If `{x}` is a real identifier, the
   ancestry is redundant — the seat already knows what it belongs to.

### The demo

```java
@GetMapping("/events/{name}/seats")
ResponseEntity<?> seatsForEvent(@PathVariable String name) {
    if (events.stream().noneMatch(e -> e.name().equals(name))) {
        return ResponseEntity.status(404)            // resolve the parent FIRST
            .body(Map.of("error", "no event named " + name));
    }
    return ResponseEntity.ok(seatsFor(name));        // one level of nesting: fine
}

@GetMapping("/seats")
ResponseEntity<?> findSeats(@RequestParam("event") String event,
        @RequestParam(value = "row", required = false) String row) {
    // ... same event check ...
    List<Seat> result = seatsFor(event).stream()
        .filter(s -> row == null || s.id().startsWith(row))
        .toList();
    return ResponseEntity.ok(result);                // deep access, flat address
}
```

```text
=== 1) one level of nesting: the seats that belong to event gig ===
GET /events/gig/seats
HTTP/1.1 200 
[{"id":"H1","held":false},{"id":"H2","held":true},{"id":"J1","held":false}]

=== 2) the parent is resolved first: seats of a missing event ===
GET /events/zzz/seats
HTTP/1.1 404 
{"error":"no event named zzz"}

=== 3) the flat alternative: deep access by query parameters ===
GET /seats?event=gig&row=H
[{"id":"H1","held":false},{"id":"H2","held":true}]
```

### Walkthrough

1. **Capture 1: nesting earning its keep.** "Which seats?" has no answer without "which
   event?" — one level, readable as a sentence: *the seats of gig*.
2. **Capture 2: parent-first in its natural home.** Not `200 []` ("that event has no
   seats") but `404` ("there is no such event") — an empty list would lie about `zzz`
   existing.
3. **Capture 3: the escape hatch.** Row H could have been
   `/events/gig/seat-maps/main/rows/H/seats` — four levels, every ancestor typed into
   every request. Instead `/seats` is top-level and context arrives as filters. Same
   answer, flat address. The deep path's smell: by the time you can identify a row, the
   ancestors are *derivable* — demanding them is ceremony.

The line: `/events/{id}/seats` — fine. `/orgs/{o}/events/{e}/sections/{s}` — the third
level should already have you reaching for `/sections?event-id={e}`.

### Where is it used?

The platform's natural nesting is in the task text: `/events/{id}/seat-maps`. GitHub
nests one level (`/repos/{owner}/{repo}/issues`) then flattens. The paper design uses at
most one nesting level and query parameters for everything deeper.

### Common mistakes

- **The ancestry chain.** `/orgs/1/events/2/tiers/3/seats/4` — four ids to ask about one
  seat whose id already implies the rest.
- **Nesting for association instead of ownership.** An event *has a* venue, but venues
  exist independently — their home is `/venues/9`; the event references it.
- **`200 []` when the parent is missing.** Claims the parent exists. Parent first.
- **Duplicating addresses.** If `/events/5/seats/H7` and `/seats/H7` both work, which is
  canonical? One address per resource; the other shape is a filter.

### Summary

- Nest one level to say "belongs to"; resolve parents before anything else; flatten with
  query parameters beyond two levels.
- An identifier that implies its ancestry makes the ancestry redundant in the URL.
- Ownership nests; mere association references.

### Practice questions

**Question 1.** A ticketing platform needs URLs for: (a) the seat maps belonging to event
7; (b) all holds placed on seat map `sm-3`, filtered to row H; (c) the venue where event
7 happens, given that venues host many events and are managed independently of any one
event. For each, write the URL and say whether you nested, used query parameters, or gave
the resource its own top-level address — and why.

**Answer.** (a) Nested, one level: `GET /events/7/seat-maps` — a seat map is meaningless
without its event; the ownership belongs in the address. (b) Query parameters on a
top-level collection: `GET /holds?seat-map-id=sm-3&row=H` — nesting would read
`/events/7/seat-maps/sm-3/holds?row=H`, three levels of ancestry to answer a question
that two filters settle; `sm-3` already identifies the map. (c) Its own top-level
address: `/venues/{id}` — venues exist independently and host many events, so
`/events/7/venue` as their only address would deny them a life of their own; the event
*references* its venue (a `venueId` field in the event's body, or a link), and the
venue's canonical home stays `/venues/9`.

**Question 2.** A code review finds this URL in a new API:
`GET /organizers/12/events/7/seat-maps/2/rows/H/seats/H7`. The reviewer rejects it,
saying "if you know the seat's id, you know everything else." Explain what the reviewer
means, write the replacement design (one URL for fetching seat H7 of seat map 2, one URL
for listing row H's seats), and state what happens to the request when someone supplies a
wrong-but-existing organizer id in the original deep URL — say, organizer 99, who does
not own event 7.

**Answer.** The reviewer means the ancestry is derivable: seat H7 of seat map 2 is fully
identified by those two values alone — the server can look up which event the map belongs
to and which organizer owns the event, so making the client supply (and the server
verify) the whole chain adds typing and failure modes while adding no information. The
replacement: `GET /seat-maps/2/seats/H7` for the single seat (one level of nesting,
justified because seat labels like H7 repeat across maps — the map is required context),
and `GET /seats?seat-map-id=2&row=H` for the row listing. In the original deep URL,
organizer 99 breaks the chain: the server must resolve path identities left to right and
check each link — organizer 99 exists, but event 7 does not belong to organizer 99, so
the correct answer is `404` (the path as a whole names nothing). And that is the design
smell in miniature: an extra level whose only contributions are one more validation to
implement, one more way to fail, and zero new information. Worse is the lazy
implementation that ignores the ancestry and serves the seat anyway — then
`/organizers/99/...` "works", the URL asserts an ownership that is false, and two
different paths serve the same resource.

---

## Lesson 36 — Pagination: pages instead of everything, and the cap that protects you

### What is it?

**Pagination** is returning a *page* of results instead of all of them. The first form is
**offset pagination**: `limit` (how many) and `offset` (how many to skip) —
`?limit=20&offset=40` reads "skip 40, give me 20": page three. Alongside it, a rule the
task states in security terms: the maximum `limit` is decided **server-side**. And once
published, these parameters are part of the **contract** — which is where **versioning**
(`/api/v1`) comes in.

### Why do we need it?

Collections grow. `GET /events` returning everything is fine with five events and a
disaster with five hundred thousand: the database reads every row, the server builds a
giant response, the network hauls it. Pagination makes cost proportional to what is
viewed. The cap exists because `limit` is *client input*: whatever number arrives, the
database obeys — the server must have the last word on how much work one request can
command.

### How does it work?

The pipeline runs in fixed order: **filter, then skip, then take**. Properties that fall
out:

- Pages tile the collection: `offset=0`, `2`, `4`… with `limit=2` — no gaps, no overlaps.
- The last page may be partial; past the end is `[]` with `200` — an empty page of a real
  collection.
- Effective limit = `min(requested, cap)` — the server quietly enforces its ceiling.

Honest weakness: `offset=100000` forces the database to count through 100,000 rows to
skip them — offset pagination degrades on large tables. Task 1.9 replaces it with keyset
pagination. It remains the right first tool: simple, stateless, good enough until tables
get big.

### The demo

Cap set to an absurdly small 3 *on purpose*, so the demo can show it working:

```java
private static final int MAX_LIMIT = 3;      // server-side cap

@GetMapping("/events")
List<ToyEvent> listEvents(
        @RequestParam(value = "q", required = false) String q,
        @RequestParam(value = "limit", required = false) Integer limit,
        @RequestParam(value = "offset", required = false) Integer offset) {
    int effectiveLimit = Math.min(limit != null ? limit : 20, MAX_LIMIT);
    int effectiveOffset = offset != null ? offset : 0;
    return events.stream()
        .filter(e -> q == null || e.name().contains(q))  // 1. filter
        .skip(effectiveOffset)                           // 2. skip past pages
        .limit(effectiveLimit)                           // 3. take one page
        .toList();
}
```

Five events exist — alpha, beta, gamma, delta, epsilon:

```text
=== 1) page one: limit=2 ===
[{"name":"alpha"},{"name":"beta"}]

=== 2) page two: limit=2&offset=2 ===
[{"name":"gamma"},{"name":"delta"}]

=== 3) page three: limit=2&offset=4 (a partial last page) ===
[{"name":"epsilon"}]

=== 4) past the end: offset=99 ===
HTTP/1.1 200 
[]

=== 5) the greedy request: limit=1000000 (cap is 3) ===
[{"name":"alpha"},{"name":"beta"},{"name":"gamma"}]
```

### Walkthrough

1. **Captures 1–3 tile the collection** — two, two, one; a client walks pages by bumping
   `offset` until a page comes back shorter than `limit` or empty.
2. **Capture 4: past the end is `200 []`** — the collection exists; this slice is empty.
3. **Capture 5 is the security point, live.** A million demanded, three returned — and
   the work done was three rows, not a million. Without a cap, `?limit=1000000` against a
   real table reads a million rows and ships a hundred-megabyte response; sent repeatedly
   from a cheap laptop it is a **denial-of-service attack** — the server starves real
   users while serving garbage. The cap turns the attack into three rows of nothing.
   Client input must never dictate unbounded server effort.
4. The full listing design assembled:
   `GET /events?city=Leeds&from=2026-07-01&to=2026-07-31&q=jazz&sort=startsAt,desc&limit=20&offset=40`
   — filters, sort, page; every piece optional; the bare collection still works.

Closing idea: these parameters are now promises. **Versioning** is the escape valve —
prefix paths with `/api/v1` so a future incompatible redesign lives at `/api/v2` while
`v1` clients keep working. Costs nothing on day one; buys the right to change your mind.

### Where is it used?

Stripe caps `limit` at 100; GitHub at 100 — ask for more, get the cap. The platform's
event search is the capstone case: half a million events, a public search box, and an
uncapped `limit` would be found by someone unfriendly in the first week.

### Common mistakes

- **No server-side cap.** Not optional polish; the difference between "slow page" and
  "outage".
- **Erroring on the greedy request.** `422` is defensible; quietly capping is kinder and
  universal practice.
- **`404` past the end.** It is an empty page of an existing collection: `200 []`.
- **No default `limit`.** Absent must not mean *everything*; the default (20) matters as
  much as the cap.
- **Promising page stability.** Rows created between page requests shift offsets —
  clients may see duplicates or miss rows. Offset pagination accepts this; keyset
  pagination (Task 1.9) exists for systems that cannot.

### Summary

- Filter → skip → take; `limit`/`offset` tile the collection; past the end is `200 []`.
- Effective limit = `min(requested, cap)`; the cap is a security control against
  denial-of-service.
- Defaults matter; offsets degrade on big tables (keyset later); `/api/v1` buys future
  freedom.

### Practice questions

**Question 1.** An events table holds 240 rows. A client requests
`GET /events?limit=100&offset=200` against an API whose server-side cap is 50 and default
limit is 20. Say how many rows come back and which ones (by position), what would come
back for the same request with `offset=300`, and what the response would be if the client
sent no `limit` and no `offset` at all.

**Answer.** The requested 100 exceeds the cap, so the effective limit is
`min(100, 50) = 50`; after skipping 200 rows, only rows 201–240 remain — 40 rows — so the
response is those 40 (a partial page, shorter than the effective limit, which also tells
the client it has reached the end). With `offset=300`: the skip goes past the table's
240 rows entirely, so the answer is `200` with `[]` — an empty page of an existing
collection, not an error. With neither parameter: defaults apply — `limit=20`,
`offset=0` — so rows 1–20 come back.

**Question 2.** A code reviewer sees a new listing endpoint with no maximum on `limit`
and comments: "this is a denial-of-service waiting to happen — and it doesn't even need a
malicious user." Explain the attack with a malicious user, then give an innocent scenario
(no attacker anywhere) that causes the same outage, and state the two-part fix.

**Answer.** The attack: anyone sends `?limit=1000000` in a loop. Each request forces the
database to read the entire table, the server to serialize an enormous response, and the
network to carry it; a handful of such requests per second from one cheap machine
saturates database, CPU and bandwidth, and legitimate requests starve — an outage
manufactured from a single unbounded parameter. The innocent version: a teammate building
an internal dashboard or mobile screen writes `limit=999999` "to fetch everything" for a
dropdown; it ships; now *every user's app* fires the monster query on every launch, and
launch-day traffic performs the denial-of-service with no attacker anywhere — the pattern
is identical, only the intent differs. The two-part fix: a server-side cap
(`effective = min(requested, MAX)`) so no request can command unbounded work, and a
sensible default when `limit` is absent so "forgot to page" never means "fetch the
table". Client input may *request*; the server *decides*.

---

## Lesson 37 — Actions that are not CRUD, and the full paper design

### What is it?

The last design tool, for operations that refuse to be create/read/update/delete: **the
action as a sub-resource**. Publishing an event is not really "updating" it — it is a
business step with rules, side effects, and its own failure modes. The pattern:
`POST /events/{id}/publish` — a verb-named *sub-path*, scoped under the noun it acts on.
The task is direct about its status: a **pragmatic compromise rather than pure REST** —
and still clearly better than `POST /publishEvent`.

### Why do we need it?

Forcing every operation into the five methods produces lies. The disguise
`PATCH /events/42 {"published": true}` pretends publishing is a field edit — but
publishing *validates* (seat map? date?), *transitions state* (draft → live, not
reversible by setting `false`), and *triggers consequences* (notifications,
searchability). Meanwhile `POST /publishEvent` throws away the noun, the address, the
grid. The sub-resource action is the middle path: verb contained, noun addressable, all
machinery — `404`, `403`, `409` — still working.

### How does it work?

1. **Reach for an action only when CRUD genuinely misdescribes the operation** — state
   transitions with rules (publish, cancel-with-refund, pay). A real field edit stays
   `PATCH`.
2. **Scope it under the item:** `POST /events/{id}/publish`. Method `POST` (not safe, not
   idempotent — twice is not the same as once). Identity stays in the path, so the whole
   status ladder applies unchanged.
3. **Keep actions rare.** Each one must earn its exception.

Why it beats `POST /publishEvent`, written down as the task asks: the action stays
attached to its noun, so the path still *names the thing acted on* — the server resolves
`/events/42` first and answers `404`/`403` about it honestly; the verb pollutes nothing
globally (`/publishEvent`, `/publishVenue`, `/cancelOrder`… is an unbounded vocabulary,
while `/events/{id}/publish` lives inside the events grid); and clients discover it where
they already look — on the event.

### The demo

Event 2: bob's unpublished "Secret Launch Party". One action endpoint, five outcomes,
all live:

```text
=== 1) publish with no credentials ===
HTTP/1.1 401 

=== 2) alice tries to publish bob's secret draft ===
HTTP/1.1 404 
{"error":"no such event"}

=== 3) bob publishes his own event ===
HTTP/1.1 200 
{"name":"Secret Launch Party","owner":"bob","published":true}

=== 4) bob publishes it AGAIN ===
HTTP/1.1 409 
{"error":"event is already published"}

=== 5) alice tries to publish it now that it is public ===
HTTP/1.1 403 
{"error":"only the owner may publish this event"}
```

The whole module converges here: no credentials → `401`; a stranger probing a secret →
the hiding `404`; the owner → `200`; a repeat → `409` (the action collides with the state
it produced — publish is `POST`-shaped, not idempotent); a known non-owner → `403`. This
ladder falls out naturally *because the identity is in the path* — `POST /publishEvent`
with an id in the body would have to reinvent it.

### The paper design — events and seat maps, /api/v1, ten endpoints

| # | Method & path | Success | Error cases |
|---|---|---|---|
| 1 | `GET /api/v1/events?city=&from=&to=&q=&sort=&limit=&offset=` | `200` list, `[]` when empty | `400` unparseable param (`from=banana`); `422` valid-but-nonsense (`from` after `to`); `429` + `Retry-After` |
| 2 | `POST /api/v1/events` | `201` + `Location: /api/v1/events/{id}` | `400` broken JSON; `422` blank name; `401` no token; `403` customer role |
| 3 | `GET /api/v1/events/{id}` | `200` object | `404` unknown — or someone else's unpublished draft (hiding) |
| 4 | `PATCH /api/v1/events/{id}` | `200` updated object | `400`/`422` body faults; `401`/`403`/`404`; `409` editing a cancelled event |
| 5 | `DELETE /api/v1/events/{id}` | `204` | `401`/`403`/`404`; `409` tickets already sold |
| 6 | `GET /api/v1/events/{id}/seat-maps` | `200` list | `404` if the *event* is missing (parent first) |
| 7 | `POST /api/v1/events/{id}/seat-maps` | `201` + `Location` | `401`/`403`/`404`; `422` malformed layout |
| 8 | `GET /api/v1/seat-maps/{id}` | `200` object | `404` — flat address; the map's id suffices |
| 9 | `GET /api/v1/seats?seat-map-id=&row=` | `200` list, `[]` for empty row | `404` if `seat-map-id` names nothing; `400` missing required `seat-map-id` |
| 10 | `POST /api/v1/events/{id}/publish` | `200` published event | `401`/`403`/`404`; `409` already published; `422` not publishable (no seat map yet) |

Checklist verified: list (1), fetch (3), create (2), update (4), delete (5), nested read
(6), action (10). Nesting never exceeds one level; the deep question went flat with query
parameters (9); identity always in the path, filters always behind `?`; `/api/v1` on
everything — room for a breaking change later without a flag day.

**Keep this page**: Phase 1 rebuilds exactly this API, and comparing the paper design to
the build is one of the most instructive things in Stage F.

### Where is it used?

GitHub merges a pull request with `PUT /repos/{o}/{r}/pulls/{n}/merge`; Stripe captures a
payment with `POST /payment_intents/{id}/capture` — verbs scoped under nouns, nowhere
free-floating.

### Common mistakes

- **Actions for field edits.** Renaming an event is `PATCH`; actions are for rules and
  side effects.
- **Free-floating verbs.** `POST /publishEvent` — no address, no ladder, unbounded
  vocabulary.
- **Actions disguised as `PATCH` on a status field.** `{"status":"published"}` invites
  `{"status":"unpublished"}`; transition rules are invisible.
- **Missing the action's own error rows.** Publish-twice is `409`; publish-before-ready
  is `422`. No error rows, no finished design.
- **An actions-first API.** If half the endpoints are verbs, the nouns were wrong —
  redesign the resources.

### Summary

- Actions as sub-resources: `POST /noun/{id}/verb` — pragmatic, contained, addressable.
- One publish endpoint exercised `401`, hiding-`404`, `200`, `409`, `403` — the module's
  ladder converging.
- The ten-endpoint paper design stands; it is rebuilt for real in Phase 1.

### Practice questions

**Question 1.** A teammate wants to let organisers cancel a published event, which must
refund all sold tickets and notify buyers. They propose `PATCH /events/42` with body
`{"status": "cancelled"}`. Make the case for `POST /events/42/cancel` instead: name two
things the PATCH disguise hides, and give the status code for each of these outcomes — a
second cancel of the same event, and a cancel attempt by a logged-in customer who did not
create the event.

**Answer.** The PATCH disguise hides, first, the **side effects**: cancelling moves money
(refunds) and sends notifications — consequences that no "edit a field" vocabulary
expresses, so a client believes it is flipping a boolean while the server is wiring
refunds; and second, the **transition rules**: `cancelled` is not a freely settable value
— you cannot un-cancel by sending `{"status":"published"}`, cancel may be forbidden in
some states, and a field-edit design makes every such rule invisible until a confusing
refusal arrives. `POST /events/42/cancel` names the business step honestly: its rules,
its refusals, and its consequences belong to the action. The codes: a second cancel of
the same event is `409 Conflict` — the request is well-formed but collides with the
current state (already cancelled), exactly like publish-twice in the demo. A cancel
attempt by a logged-in customer who did not create the event is `403 Forbidden` —
identity known and valid, permission denied; the event is published, so its existence is
public and there is nothing to hide with a `404`.

**Question 2.** In the ten-endpoint design above, endpoint 6
(`GET /api/v1/events/{id}/seat-maps`) returns `404` when event `{id}` does not exist, but
endpoint 9 (`GET /api/v1/seats?seat-map-id=sm-3&row=Z`) returns `200` with `[]` when row
Z has no seats. Both are "nothing came back" — justify the different codes using the
identity-versus-filter rule, and say what endpoint 9 should return if `seat-map-id` names
no existing seat map.

**Answer.** Endpoint 6's `{id}` is an identity in the path: the path names "the seat maps
*of event {id}*", and if event `{id}` does not exist the path as a whole names nothing —
`404`, because answering `200 []` would assert that the event exists and merely has no
seat maps, which is false. Endpoint 9's `row=Z` is a filter: the seat map exists, its
seats collection exists, and "which of them are in row Z?" has a perfectly healthy answer
— none — so `200` with `[]`. The question was answered; zero is a count, not a failure.
If `seat-map-id` names no existing seat map, endpoint 9 returns `404`: although it
travels as a query parameter, `seat-map-id` carries an identity — it names the context
the whole question depends on — and the identity-versus-filter rule is about *meaning*,
not syntax position. A reference to a thing that must exist resolves first, exactly like
a path parent; only after it resolves does the empty-set-is-fine rule apply to what `row`
narrows.

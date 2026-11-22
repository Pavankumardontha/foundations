# Task F4.2 — Methods, and the promise each one makes

## Lesson 10 — What a method is, and the five you will use

### What is it?

The **method** is the action word at the start of a request's first line — the `GET` in
`GET /hello HTTP/1.1`. It tells the server what *kind* of operation you want: read the
thing, create one, replace one, change part of one, or remove one.

### Why do we need it?

A path like `/events/7` names a thing. Naming is not enough: several things might be
wanted *done* with event 7 — look at it, change it, delete it. The method is the second
piece of information that says which.

Because the meanings of the action words are fixed by the protocol, software that has
never seen your API can still reason about your requests. A cache knows it may store the
answer to a read. A crawler knows following a read link is harmless. That machinery works
only if everyone means the same thing by each word.

### How does it work?

The server routes every request by the **pair** of method and path. `(GET, /hello)` and
`(POST, /hello)` are two different destinations. If the pair is not registered, the
request is refused.

| Method | Contract, in plain words | Ticketing example |
| --- | --- | --- |
| `GET` | Read the thing. Change nothing. | Show event 7 |
| `POST` | Create something new, or trigger an action. | Create a new event |
| `PUT` | Replace the whole thing with what I am sending. | Overwrite event 7 completely |
| `PATCH` | Change only the parts I mention. | Change just event 7's name |
| `DELETE` | Remove the thing. | Delete event 7 |

Each row is a **promise**. Two of the promises have precise names — *safe* and
*idempotent* — covered in the next two lessons.

### Simple example (run for real)

The toy app registers exactly one pair: `(GET, /hello)`. The `-X` flag tells `curl` which
method word to write; `-i` shows the status line and headers.

**`GET /hello`:**

```http
HTTP/1.1 200 
Content-Type: application/json

{"message":"hello","id":1}
```

**`POST /hello` — same path, different word:**

```http
HTTP/1.1 405 
Allow: GET
Content-Type: application/json

{"timestamp":"2026-10-21T12:25:25.614+00:00","status":405,"error":"Method Not Allowed","path":"/hello"}
```

**`DELETE /hello`:** also `405`, also `Allow: GET`.

### Walkthrough

1. `GET /hello`: the pair `(GET, /hello)` is registered → handler runs → `200` with JSON.
2. `POST /hello`: the pair `(POST, /hello)` is not registered → `405`, meaning "I know
   this path, but not with that method". Compare `404`: "I do not know this path at all".
3. The `Allow: GET` header in the refusal lists which methods the path does support. On a
   real 405, this header is the server telling you the fix.
4. The refusal body is Spring Boot's standard error JSON: status repeated, a label, the
   path. A later task replaces this default with something better.

### Where is it used?

- In Java, the method is chosen by annotation: `@GetMapping`, `@PostMapping`,
  `@PutMapping`, `@PatchMapping`, `@DeleteMapping` — each registers a (method, path) pair.
- In API design, every endpoint is a method-plus-path pair, with the method carrying the
  operation's meaning.

### Common mistakes

- **Using `POST` for everything.** Bytes flow, but every promise is thrown away: caches
  cannot cache, clients cannot safely retry, developers cannot guess what endpoints do.
- **Using `GET` for something that changes state.** The dangerous inverse — browsers and
  crawlers call `GET` freely because it promises to change nothing.
- **Thinking the server enforces the promise.** Nothing stops a handler from deleting data
  on `GET`. The method is a contract humans keep; the web's machinery trusts it on faith.

### Summary

- The method is the action word; the server routes on (method, path) pairs.
- `GET` reads, `POST` creates or triggers, `PUT` replaces whole, `PATCH` changes part,
  `DELETE` removes.
- `405 Method Not Allowed` = known path, wrong method; read its `Allow` header.
- The contracts are promises kept by programmers, trusted by infrastructure.

### Practice questions

**Question 1.** A ticketing API stores venues. For each operation below, pick the method
from `GET`, `POST`, `PUT`, `PATCH`, `DELETE`:

1. Show venue 12.
2. Add a brand-new venue.
3. Venue 12 moved buildings: here is its complete new record, replace the old one.
4. Fix only a typo in venue 12's name, touching nothing else.
5. Venue 12 closed down: remove it.

**Answer.** (1) `GET /venues/12` — a pure read. (2) `POST /venues` — creating something
new. (3) `PUT /venues/12` — replacing the whole record with a complete new version.
(4) `PATCH /venues/12` — changing only the mentioned part. (5) `DELETE /venues/12` —
removing it.

**Question 2.** A server has one handler registered: the pair (`GET`, `/events`). A client
sends `POST /events`, and the server answers with status `405` and a header `Allow: GET`.
A teammate says: "405 means the path does not exist." Correct the teammate: what does 405
actually mean, which error code covers "path does not exist", and what is the `Allow`
header telling the client?

**Answer.** 405 means the opposite of what the teammate said about the path: the server
*does* know the path `/events`, but not combined with the method `POST`. The lookup key is
the pair of method and path, and only (`GET`, `/events`) is registered. "Path does not
exist" is `404 Not Found`. The `Allow: GET` header tells the client which methods this
path does support — it is the server pointing at the fix: retry as a `GET`, or accept that
creating things here is not offered.

## Lesson 11 — Safe: the methods that change nothing

### What is it?

A method is **safe** when it promises that calling it changes nothing on the server. It
reads; it never writes. Of the five methods, exactly one is safe: `GET`. The other four
exist to change things, so none of them is safe.

### Why do we need it?

A huge amount of software calls `GET` on URLs without any human asking it to:

- A browser **prefetches** links on the page you are reading.
- A chat app fetches any pasted URL to show a **link preview**.
- A search engine's **crawler** follows every link it finds, millions per hour.
- A load balancer calls a **health check** URL every few seconds, around the clock.
- A **cache** stores a `GET` answer and replays it without consulting the server.

None of these asks permission. The arrangement works only because of one shared promise:
a `GET` changes nothing, so calling it costs nothing but bandwidth. Safety is the
load-bearing assumption of the web's machinery, not politeness.

### How does it work?

There is no enforcement. Safety is a contract in two halves:

- **The server author's half:** every handler registered under `GET` must only read. No
  row written, no email sent, no money moved — no **side effect** (the general word for
  any change a request causes).
- **Everyone else's half:** prefetchers, crawlers, caches and health checkers take the
  promise on faith and call `GET` freely.

One precision: safe does **not** mean "the same answer every time". `GET /events` may
return three events today and five tomorrow because organisers added two in between. The
data changed — but not *because of* the `GET`. Safe promises only that **the call itself
causes no change**.

### Simple example (run for real)

Three `GET /hello` calls in a row against the toy app:

```
{"message":"hello","id":1}
{"message":"hello","id":1}
{"message":"hello","id":1}
```

The entire handler that ran three times:

```java
@GetMapping("/hello")
HelloResponse hello() {
    return new HelloResponse("hello", 1);
}
```

### Walkthrough

1. Three requests arrived; the handler ran three times.
2. The handler constructs an answer and returns it. No assignment to stored state, no file
   write, no database call. Reading the code is how you verify safety — no tool does it
   for you.
3. After three calls — or a thousand — the server is in exactly the state it started in.
4. A crawler hitting this ten thousand times costs the owner nothing. That is why nothing
   automated asks before calling a `GET`.

### Where is it used?

- Health probes `GET` your app every few seconds forever. If that `GET` wrote anything,
  monitoring would corrupt the system slowly and constantly.
- Caching applies only to safe methods. Replaying stored `GET` answers is free speed.
- Every link anywhere is an implicit `GET` that anything might follow at any time.

### Common mistakes

- **A `GET` that changes state.** The classic is "cancel my order" reachable by plain
  link: prefetchers and link previews cancel real orders with no human clicking. A later
  lesson makes this happen for real.
- **Thinking safe means "frozen answer".** Safe is about what the call *causes*, not what
  it *returns*.
- **"Harmless" writes in a `GET`** — a view counter, a "last seen" timestamp. Each means
  crawlers and health checks silently write to your database all day.

### Summary

- Safe = the call changes nothing. Only `GET` (of the five) is safe.
- The web's automation — prefetch, previews, crawlers, caches, probes — relies on it.
- Safety is verified by reading handler code, enforced by nobody.
- Changing data between calls does not violate safety; causing change does.

### Practice questions

**Question 1.** A load balancer calls `GET /health` on an app every five seconds, around
the clock — roughly 17,000 calls a day. The owner never notices, and nothing in the system
drifts or accumulates. Which property of `GET` makes this arrangement harmless? Now
suppose a developer edits the `/health` handler so it also inserts one row into a
`health_checks` database table per call. Is the endpoint still safe, and what happens to
the database over a month?

**Answer.** The harmlessness comes from safety: the call causes no change, so 17,000 calls
a day leave the system exactly as it was. With the insert added, the handler now has a
side effect, so the endpoint is no longer safe in practice — even though it still answers
under the method whose contract says "I change nothing". Over a month, monitoring alone
writes roughly half a million rows into the table. The monitoring system is now a writer
to the database, which is exactly the kind of silent, constant corruption the safety rule
exists to prevent.

**Question 2.** On Monday, `GET /events` returns a list of 3 events. On Tuesday, the same
request returns 5 events, because organisers created two more overnight using
`POST /events`. A teammate says: "the answer changed, so this `GET` is not safe". Is the
teammate right? State precisely what the safety promise covers and what it does not.

**Answer.** The teammate is wrong. Safety covers what the call *causes*, not what it
*returns*. The `GET` did not create the two new events; two `POST` requests did. The world
moved between Monday and Tuesday, and the `GET` simply reported the new truth. The safety
promise says: the system is the same after the call as before it. That held on both days.
A changing answer is allowed; a caused change is not.

## Lesson 12 — Idempotent, and the counting test

### What is it?

An operation is **idempotent** when doing it once and doing it five times leave the system
in the **same end state**. It is about the state afterwards — not about getting an
identical reply each time.

### Why do we need it?

Networks lose replies. A client that hears nothing cannot tell whether the request was
lost on the way there (work never happened) or the answer was lost on the way back (work
happened). The only move available is to send the same request again. If the operation is
idempotent, the repeat is harmless. If not, every retry risks doubling something: two
orders, two charges, two emails.

### How does it work?

The **counting test**: *if this ran five times instead of once, would the end state be
different?*

"**Set** something to a value" passes: setting a name to `Big Hall` five times leaves the
name `Big Hall`. "**Add** one more" fails: adding an order five times leaves five orders.

| Method | Counting test | Idempotent? |
| --- | --- | --- |
| `GET` | Reads five times, changes nothing five times. | Yes (and safe) |
| `PUT` | "The resource **is now** this" five times = same final resource. | Yes |
| `DELETE` | Gone after one, still gone after five. | Yes |
| `PATCH` | "Set name to X" passes; "append one entry" fails. | Not guaranteed |
| `POST` | "Create one more" five times = five things. | **No** |

Relationship to safe: every safe method is automatically idempotent (changing nothing five
times is still nothing). But idempotent does not mean safe: `PUT` and `DELETE` change the
system, in a way where repeats add nothing. Safe is the stronger promise.

### Simple example (compiled and run, Java 21)

```java
public class CountingTest {
    public static void main(String[] args) {
        // Operation 1 — "set the venue name to Big Hall" (the shape of PUT)
        String setOnce = "Old Hall";
        setOnce = "Big Hall";
        String setFive = "Old Hall";
        for (int i = 0; i < 5; i++) setFive = "Big Hall";
        System.out.println("set once : " + setOnce);
        System.out.println("set five : " + setFive);

        // Operation 2 — "add one order" (the shape of POST)
        int addOnce = 0;
        addOnce = addOnce + 1;
        int addFive = 0;
        for (int i = 0; i < 5; i++) addFive = addFive + 1;
        System.out.println("add once : " + addOnce);
        System.out.println("add five : " + addFive);
    }
}
```

Output:

```
set once : Big Hall
set five : Big Hall
add once : 1
add five : 5
```

### Walkthrough

1. The set operation left `Big Hall` after one run and after five. The counting test
   passes. This is `PUT`'s shape: the request carries the complete final value, so repeats
   re-state it.
2. The add operation left `1` after one run and `5` after five. The test fails. This is
   `POST`'s shape: every repeat is "one more".
3. Idempotency is a property of the *shape of the instruction*, not of HTTP machinery.
   "Make the state X" repeats harmlessly; "change the state by one step" does not.

### Where is it used?

- Every retry decision: a timed-out `PUT` or `DELETE` may be freely resent; a `POST` may not.
- Phase 5 (the reservation engine) exists because "charge the customer" is add-shaped and
  must be made retry-proof.
- Proxies and client libraries auto-retry idempotent methods but never `POST` — the method
  word is the only promise they can see.

### Common mistakes

- **Confusing "same end state" with "same reply".** Deleting twice gives different replies
  but an identical end state — still idempotent.
- **Confusing idempotent with safe.** `PUT` changes data (not safe) yet repeats harmlessly
  (idempotent). Independent questions.
- **Assuming `PATCH` is idempotent.** Only its set-shaped uses are.

### Summary

- Idempotent = once and five times end in the same state. Judge by end state, not replies.
- The counting test is the entire definition in one question.
- `GET`, `PUT`, `DELETE`: idempotent. `POST`: no. `PATCH`: depends on the patch's shape.
- Safe implies idempotent; idempotent does not imply safe.

### Practice questions

**Question 1.** Apply the counting test — "if this ran five times instead of once, is the
end state different?" — to each of these ticketing operations, and say which are
idempotent:

1. "Set seat H7's status to HELD."
2. "Increase the number of seats held by this customer by one."
3. "Send the customer a confirmation email."

**Answer.** (1) Idempotent: after one run or five, seat H7's status is HELD. Set-shaped.
(2) Not idempotent: one run leaves the count one higher; five runs leave it five higher.
Add-shaped. (3) Not idempotent: each run puts one more email into the world. After five
runs the customer has five emails. "Send" is add-shaped even though no database row
changes — the end state of the world includes the emails sent.

**Question 2.** A teammate says: "`PUT` modifies data, so it can't be idempotent —
idempotent things don't change anything." Untangle the two ideas: what is the promise
called when a call changes nothing at all, what is the promise called when repeats add
nothing beyond the first call, and which of the two does `PUT` make? Use `PUT /venues/12`
with the body `{"name":"Big Hall"}` as your example.

**Answer.** "The call changes nothing at all" is **safe** — that is `GET`'s promise. "The
first call may change things, but repeats add nothing" is **idempotent** — that is `PUT`'s
promise. The teammate has merged the two. `PUT /venues/12` with `{"name":"Big Hall"}`
changes the venue's record on the first call, so it is not safe. Called five times, the
record ends as the same `Big Hall` version every time — the end state after five calls
equals the end state after one — so it is idempotent. Changing data and being repeatable
are independent properties.

## Lesson 13 — Why retries happen: the lost reply

### What is it?

A **retry** is sending the same request again because no reply arrived. **At-least-once**
is what this does to the receiving side: a message may be handled more than once, and
nothing the sender does can fully prevent that. You can only make duplicates harmless.

### Why do we need it?

A customer taps "Pay" on a phone with two bars of signal. The request leaves. Silence.
That silence has two possible causes, and they are **indistinguishable** from the phone:

1. The request was lost on its way to the server. The payment never happened.
2. The server received it, charged the card — and the *reply* was lost. The payment happened.

There is no third option where the client finds out without asking again. So it asks again
— automatically, or because an impatient human taps twice. If cause 1 was true, the retry
saves the sale. If cause 2 was true, the retry charges the card twice.

### How does it work?

1. Client sends the request and starts a clock (a **timeout**).
2. The clock runs out with no reply.
3. The client now holds an ambiguous fact: "no answer" — which is *not* the fact "nothing
   happened".
4. The client resends.
5. The server may now be seeing the same logical request for the second time, with no
   built-in way to know it is a repeat.

Across all branches, one logical request may be handled **zero, one, or several times**.
That is at-least-once reality. Duplicates arrive, so operations must be shaped to survive
them — the idempotency property: "set"-shaped survives, "add"-shaped doubles.

### Simple example (compiled and run, Java 21)

The server always does the work; the reply is lost half the time; the client retries until
it hears back. Fixed seed (`5`) so the run is repeatable:

```java
import java.util.Random;

public class RetrySimulation {
    static int ordersCreated = 0;          // the server's reality
    static Random network = new Random(5); // fixed seed, so the run is repeatable

    // One call to "POST /orders". The server ALWAYS does the work.
    // Then the reply is lost in transit 50% of the time.
    static boolean callCreateOrder() {
        ordersCreated++;                          // work happens on the server
        return network.nextInt(100) < 50;         // does the reply make it back?
    }

    public static void main(String[] args) {
        int attempts = 0;
        boolean heardBack = false;
        while (!heardBack) {                      // the client retries until it hears a reply
            attempts++;
            heardBack = callCreateOrder();
            System.out.println("attempt " + attempts + ": "
                + (heardBack ? "reply received" : "no reply (timeout) -> client retries"));
        }
        System.out.println("client believes: 1 order was created");
        System.out.println("server reality : " + ordersCreated + " orders were created");
    }
}
```

Output:

```
attempt 1: no reply (timeout) -> client retries
attempt 2: no reply (timeout) -> client retries
attempt 3: no reply (timeout) -> client retries
attempt 4: reply received
client believes: 1 order was created
server reality : 4 orders were created
```

### Walkthrough

1. Attempts 1–3: the server created an order each time; each reply vanished; the client
   learned nothing.
2. Attempt 4: a reply made it. The client reasonably concludes its one request worked.
3. The final two lines are the whole lesson: **the client believes 1; the truth is 4.**
   Nobody misbehaved. Duplication is the default outcome of retrying a non-idempotent
   operation.
4. If the operation were set-shaped ("set seat H7 to HELD"), four executions would leave
   the same end state, and the drift would disappear. The retry loop is identical; the
   operation's shape decides everything.

### Where is it used?

- Every client has timeouts; most have automatic retries, often buried in library defaults.
- Phase 5 builds the server-side answer for `POST`-shaped operations: recognising repeats.
- Humans are retriers too: refresh, double-tap, "it hung so I clicked again".

### Common mistakes

- **"Then let's never retry."** Every lost request becomes a silently lost order — and
  users retry manually anyway, so you get duplicates *and* lost sales.
- **"Let's make the network reliable."** Not on offer. Exactly-once delivery over an
  unreliable network is provably impossible; hence at-least-once plus harmless duplicates.
- **Treating a timeout as proof of failure.** It is proof of *ignorance*. Acting as if "no
  reply" means "didn't happen" is how double charges are born.

### Summary

- No reply has two indistinguishable causes: work never happened, or only the reply died.
- Clients must retry; therefore servers see duplicates; therefore at-least-once is reality.
- One logical request may execute zero, one, or many times.
- Idempotent operations make retries free; add-shaped operations make them dangerous.

### Practice questions

**Question 1.** A client sends "charge customer 81 twenty pounds" to a payment server.
After ten seconds of silence, the client's timeout fires. List the two states the server
might be in at that exact moment, and explain why the client cannot distinguish them. Then
say which property the charge operation would need for "just resend it" to be a harmless
decision.

**Answer.** State A: the server never received the request — no charge happened. State B:
the server received it and charged the twenty pounds, and only the reply was lost. The
client cannot distinguish them because its only source of information is the reply, and in
both states there is no reply — the silence is identical. For a blind resend to be
harmless, the operation would need to be idempotent: running it a second time must leave
the same end state as running it once. A raw "charge twenty pounds" is add-shaped, so it
lacks that property; it must either be reshaped or made recognisable as a repeat.

**Question 2.** A developer, scared of double charges, configures the ticket-shop client
to never retry anything. A customer on hotel wifi taps "Buy" and the request is lost
before reaching the server. Describe what the customer experiences, what the customer does
next, and why the duplicates problem is still not solved.

**Answer.** The customer sees the app wait and then show an error or an endless spinner.
No ticket, no charge — the request never arrived. The customer then does what every human
does: taps "Buy" again. That tap is a retry; the developer only moved retrying from
software into the customer's thumb. If the first request had actually been processed and
only its reply lost, the second tap creates the duplicate anyway. Never-retrying thus
loses sales when requests vanish and still allows duplicates when replies vanish. The
network's at-least-once nature cannot be configured away; duplicates must be made harmless
on the server side instead.

## Lesson 14 — `POST` twice makes two things: see it happen

### What is it?

The non-idempotency of `POST`, observed on a real server. The same request, byte for byte,
sent twice — and the system ends up with two things.

### Why do we need it?

The counting test said `POST` fails it; the retry simulation showed a client creating four
orders while believing in one. The missing piece is the server's side on real software:
what does a correct server do when the same `POST` arrives again? It happily does the work
again. That is not a bug, and seeing it cures the instinct to call it one.

### How does it work?

The toy app's endpoint (pasted per the task; annotations are explained properly in F4.7):

```java
record ToyEvent(String name) {}

private final List<ToyEvent> events = new ArrayList<>();

@PostMapping("/events")
List<ToyEvent> create(@RequestBody ToyEvent event) {
    events.add(event);
    return events;
}
```

- `@PostMapping("/events")` registers the pair (`POST`, `/events`).
- `@RequestBody ToyEvent event`: take the JSON body, turn it into a `ToyEvent`.
- `events.add(event)` is **add-shaped**, and nothing asks "have I seen this before?". The
  server cannot ask: two identical requests are two separate messages, it has no memory of
  the first (statelessness) and no marker saying "this is a repeat".
- (The in-memory list is hidden state — fine for a toy, breaks with two copies, vanishes
  on restart. Never for real.)

### Simple example (run for real)

Three times the identical command:

```bash
curl -s -H 'Content-Type: application/json' -d '{"name":"Coldplay Live"}' http://localhost:8080/events
```

Outputs, in order:

```
[{"name":"Coldplay Live"}]
[{"name":"Coldplay Live"},{"name":"Coldplay Live"}]
[{"name":"Coldplay Live"},{"name":"Coldplay Live"},{"name":"Coldplay Live"}]
```

### Walkthrough

1. First request: parsed, appended, list of one.
2. Second request: identical bytes. The server cannot know this is "the same" event rather
   than a second event that genuinely has the same name — two bands really can both play a
   "Coldplay Live" tribute night. It did the work. List of two.
3. Third: list of three.
4. Splice in the lost-reply story: request one processed, reply lost, client retried. The
   client's screen says "created once"; the server holds two. With `POST /orders` instead
   of `POST /events`, that is a customer charged twice. This output *is* that bug, on toy
   data.
5. The server behaved correctly. `POST` means "one more". The fix cannot be "refuse
   repeated bodies" — identical bodies can be legitimate. The client must mark repeats
   explicitly; that machinery comes later (`Idempotency-Key`, Phase 5).

### Where is it used?

- Every duplicate-order, duplicate-signup, duplicate-payment incident has this shape.
- Proxies and client libraries auto-retry `GET`, `PUT`, `DELETE` but refuse to auto-retry
  `POST` — the method word is the only promise they can see.

### Common mistakes

- **Calling the server buggy.** It is keeping `POST`'s contract to the letter.
- **Deduplicating on body content.** Sameness of *content* is not sameness of *intent*;
  only the client knows it is retrying.
- **Never retrying.** Lost sales, and humans retry anyway.

### Summary

- A correct `POST` handler does the work again for a repeated request.
- Identical bodies are not evidence of a retry; intent cannot be inferred from content.
- Duplicate orders = lost reply + retry + add-shaped `POST`. All three are normal.
- The eventual fix is the client marking repeats; the server storing results per mark.

### Practice questions

**Question 1.** A server has this handler (the whole relevant code is shown):

```java
record ToyEvent(String name) {}
private final List<ToyEvent> events = new ArrayList<>();

@PostMapping("/events")
List<ToyEvent> create(@RequestBody ToyEvent event) {
    events.add(event);
    return events;
}
```

A client sends `POST /events` with body `{"name":"Quiz Night"}`. The server processes it,
but the reply is lost, so the client's timeout fires and it sends the identical request
again. This one succeeds. What does the server's list now contain, what does the client
believe happened, and did either side do anything wrong?

**Answer.** The list contains two entries: `[{"name":"Quiz Night"},{"name":"Quiz Night"}]`.
The client believes exactly one Quiz Night was created — it sent one logical request and
finally got one confirmation. Neither side did anything wrong. The client could not
distinguish "request lost" from "reply lost", so retrying was rational. The server kept
`POST`'s contract — "one more" — both times, and had no way to know the second message was
a repeat rather than a genuine second event. The duplication is the natural product of a
lost reply plus a non-idempotent operation. That is why the fix needs new machinery rather
than better behaviour from either side.

**Question 2.** A proxy sits between clients and servers, auto-retrying requests that time
out. It happily resends timed-out `PUT` and `DELETE` requests, but is configured to never
resend a timed-out `POST`. Explain the reasoning, and name the single piece of information
the proxy would need — which the plain request does not carry — to make resending a `POST`
acceptable.

**Answer.** `PUT` and `DELETE` are idempotent: however many times the proxy resends them,
the end state equals the end state of one execution, so automatic retries cannot do damage.
`POST` promises nothing of the kind — a resend may create a second order or trigger a
second charge, and the proxy cannot tell. The missing piece of information is the identity
of the *attempt*: a marker saying "this message is a repeat of attempt X, not a new
intent". The plain request carries no such marker; a client-generated value (sent in a
header, the `Idempotency-Key` pattern) supplies it, letting the server recognise the
repeat and return the stored first result instead of doing the work again.

## Lesson 15 — `DELETE` twice: same end state, different replies

### What is it?

Calling `DELETE` on the same thing twice gets two different replies — first "done" (`204`),
then "there is nothing here" (`404`) — and yet `DELETE` is still idempotent.

### Why do we need it?

The apparent contradiction causes real mistakes: clients that retry a timed-out `DELETE`,
get `404`, and show the user an error for an operation that succeeded; developers who
conclude `DELETE` "isn't really idempotent" and disable its auto-retry. It is also a
favourite interview probe.

### How does it work?

Idempotent means once and five times leave the **same end state**. The definition says
nothing about replies. Replies describe each call's own experience; end state describes
the world.

| Call | State before | What the call does | State after | Reply |
| --- | --- | --- | --- | --- |
| 1st `DELETE` | thing exists | removes it | **gone** | `204` — done, nothing to show |
| 2nd `DELETE` | thing gone | nothing to remove | **gone** | `404` — nothing here |

End state after one call: gone. After two: gone. Identical — idempotent. The replies
differ because each call truthfully reported a different situation.

(`204` is in the success family: "it worked, deliberately no body".)

### Simple example (run for real)

The toy app's delete handler:

```java
@DeleteMapping("/events/{name}")
ResponseEntity<Void> deleteEvent(@PathVariable String name) {
    boolean removed = events.removeIf(e -> e.name().equals(name));
    if (removed) {
        return ResponseEntity.noContent().build();   // 204: done, nothing to show
    }
    return ResponseEntity.notFound().build();        // 404: nothing here by that name
}
```

(`ResponseEntity` lets the handler pick the status code itself — details in a later task.)

The session:

```
=== create one event ===
[{"name":"quiz-night"}]
=== DELETE, first time ===
HTTP/1.1 204 
=== DELETE, second time, identical request ===
HTTP/1.1 404 
=== prove the end state: create a probe event, list comes back ===
[{"name":"probe"}]
```

### Walkthrough

1. One event existed: `quiz-night`.
2. First `DELETE`: found it, removed it, `204`. State: empty list.
3. Second `DELETE`: found nothing, `404`. State: still the empty list. The second call
   changed nothing — the idempotency promise doing its job.
4. The probe `POST` proves the end state: only `probe` is in the list.
5. The retry story this enables: a timed-out `DELETE` is resent and gets `404`. Correct
   reading: *deletion confirmed — the thing is definitely gone.* Good news in a gloomy
   status code.

### Where is it used?

- Retry policies resend timed-out `DELETE`s freely.
- Cleanup jobs can crash halfway and be re-run; already-deleted items answer `404` and the
  end state converges.
- Client code: after a retried `DELETE`, treat `404` as success.

### Common mistakes

- **Surfacing the second `404` as a user-facing error.** The delete worked.
- **Making the handler always return `204`.** Some APIs do; understand it hides
  information (did anything exist?), and idempotency was never broken in the first place.
- **Generalising to "replies never matter".** They matter per call; they just do not
  measure idempotency. Judge that by end state alone.

### Summary

- `DELETE` twice: `204` then `404`; end state identical; idempotent.
- Replies report each call's experience; idempotency is judged on end state.
- A `404` answering a `DELETE` retry means "confirmed gone".
- Repeat it until it stops feeling contradictory — then it is yours.

### Practice questions

**Question 1.** A client sends `DELETE /orders/55`. The server processes it — order 55 is
removed — but the reply is lost. The client times out and resends `DELETE /orders/55`,
receiving `404 Not Found`. The client's developer writes code that shows the user:
"Error: order could not be deleted." Explain what actually happened end to end, whether
order 55 still exists, and what the code should do with that `404` instead.

**Answer.** The first request succeeded: order 55 was removed; only the reply died on the
way back. The retry then asked to delete something already gone, and the server truthfully
answered `404` — nothing here by that name. Order 55 does not exist; the user got exactly
what they asked for. The error message is wrong twice over: the operation succeeded, and
the user did nothing odd. The code should treat a `404` answering a `DELETE` — especially
a retried one — as confirmation of success: the thing is definitely gone, which is the end
state the user wanted.

**Question 2.** Using the definition "idempotent = once and five times leave the same end
state", evaluate this claim: "`DELETE` returned `204` the first time and `404` the second,
two different results, so the operation had two different effects — not idempotent."
Separate the two words the claim confuses, and state what each call's *effect on state*
actually was.

**Answer.** The claim confuses **reply** with **effect**. The reply is what each call
reported back; the effect is what each call changed. Call one's effect: the order went
from existing to gone. Call two's effect: none at all — gone stayed gone. So the end state
after one call and after two calls is identical, which is the entire content of the
idempotency definition. The differing replies (`204`, then `404`) are truthful reports of
two different situations the calls found, and the definition never mentions replies.
Different words, same world: still idempotent.

## Lesson 16 — `PUT` vs `PATCH`: replace it all, or change a part

### What is it?

Both update an existing thing, but they say different sentences. `PUT` says: "here is the
**complete** resource — replace what you have with this." `PATCH` says: "change **only the
parts I mention**, touch nothing else."

### Why do we need it?

The difference decides whether fields silently vanish. A `PUT` body that leaves a field
out is not saying "keep that field" — it is saying "the new version has nothing there".
The server obeys, no error appears, and the data is gone.

### How does it work?

- **`PUT`:** the body *is* the resource. After the call, the resource equals the body.
  Whatever the body omits, the resource no longer has. This makes `PUT` idempotent — "the
  venue **is** this" five times leaves the same venue.
- **`PATCH`:** the body is a list of changes. Mentioned fields change, unmentioned fields
  stay. Idempotency is not guaranteed — "set name to X" repeats harmlessly; "add 10 to
  price" does not.

### Simple example (run for real)

The toy app got a venue and three handlers:

```java
record Venue(String name, String city) {}

private Venue venue = new Venue("Old Hall", "Leeds");

@GetMapping("/venue")
Venue getVenue() { return venue; }

@PutMapping("/venue")
Venue putVenue(@RequestBody Venue replacement) {
    venue = replacement;                 // the body IS the new venue, whole and entire
    return venue;
}

@PatchMapping("/venue")
Venue patchVenue(@RequestBody Venue changes) {
    venue = new Venue(                   // keep old values for fields not mentioned
        changes.name() != null ? changes.name() : venue.name(),
        changes.city() != null ? changes.city() : venue.city());
    return venue;
}
```

Both updates send the **identical body**, `{"name":"Big Hall"}`:

```
=== starting state ===
{"name":"Old Hall","city":"Leeds"}
=== PUT with only the name field ===
{"name":"Big Hall","city":null}
=== reset to starting state ===
{"name":"Old Hall","city":"Leeds"}
=== PATCH with only the name field ===
{"name":"Big Hall","city":"Leeds"}
```

### Walkthrough

1. `PUT {"name":"Big Hall"}`: the whole venue was replaced by the body. No city in the
   body, so no city in the venue — **Leeds is gone**, with no warning or error.
2. `PATCH {"name":"Big Hall"}`: only the mentioned field changed. City survived.
3. Same body, opposite outcomes. The method word alone changed what the bytes meant.
4. Honest footnote: the `PATCH` handler treats a missing field as "keep the old value" by
   checking for `null`. That means it cannot express "erase the city" — "field absent" and
   "field set to null" look identical after parsing. This is why `PATCH` is genuinely hard;
   a full lesson on it comes in the JSON task.

**The out-of-date client scenario (the task's question):**

1. Monday: client A fetches `{"name":"Old Hall","city":"Leeds"}` and keeps a form open.
2. Tuesday: client B corrects the city to `York`.
3. Wednesday: client A renames and saves, sending `PUT` with its complete but **stale**
   copy: `{"name":"New Hall","city":"Leeds"}`.
4. Result: the rename lands, the city silently reverts to `Leeds`. B's correction is
   destroyed. Every request was valid; every response was a success.

**`PUT` is the dangerous one when the client is out of date**: its "complete" body
re-asserts every stale value and erases every omitted field. A `PATCH` touching only
`name` would have left `York` alone.

### Where is it used?

- `PUT`: full-document saves — admin forms holding every field, config uploads.
- `PATCH`: targeted edits — a rename, a toggle, a price fix. Most everyday updates.
- Proper concurrent-edit protection uses headers met later; `PATCH` shrinks the blast
  radius but does not remove the race.

### Common mistakes

- **Sending a partial body with `PUT`.** Works in tests that send every field; loses data
  in production the first time a client omits one.
- **Assuming the server merges `PUT` bodies.** Merging is `PATCH`'s job.
- **Believing `PATCH` is always safe.** Safer against stale omissions, but not guaranteed
  idempotent and tricky around "absent vs null". The two methods say different things;
  neither is simply better.

### Summary

- `PUT`: the body is the whole new resource; omitted = erased; idempotent.
- `PATCH`: the body lists changes; unmentioned fields survive; idempotency depends.
- Same bytes under different methods produce opposite outcomes.
- Stale complete copies make `PUT` the silent destroyer of other people's edits.

### Practice questions

**Question 1.** A venue is stored as `{"name":"Old Hall","city":"Leeds","capacity":500}`.
A client sends this body: `{"name":"New Hall","capacity":500}` — first as a `PUT /venue`,
then (after the venue is reset to the stored state above) as a `PATCH /venue`, where the
server's patch rule is "change only the fields present in the body". What is the venue's
city after the `PUT`? After the `PATCH`? Explain each in one sentence.

**Answer.** After the `PUT`, the city is gone (`null`): `PUT` replaced the entire venue
with the body, and the body contains no city, so the new venue has none. After the
`PATCH`, the city is still `Leeds`: the body mentions only `name` and `capacity`, so the
patch rule changed those two and left `city` untouched.

**Question 2.** Two clients both fetch `{"name":"Old Hall","city":"Leeds"}` at 9:00. At
9:05, client B changes the city to `York` through the API. At 9:10, client A — still
holding the 9:00 copy — submits a rename. Describe the final state in two worlds: one
where client A sends `PUT {"name":"New Hall","city":"Leeds"}`, and one where client A
sends `PATCH {"name":"New Hall"}`. In the world where B's work is lost, explain why no
error appeared anywhere.

**Answer.** In the `PUT` world, the final state is `{"name":"New Hall","city":"Leeds"}`:
A's stale copy re-asserted the old city, silently overwriting B's `York`. In the `PATCH`
world, the final state is `{"name":"New Hall","city":"York"}`: A's patch touched only the
name, so B's correction survived. No error appeared in the `PUT` world because every
message was individually valid: B's update succeeded, and A's `PUT` was a well-formed
complete venue that the server replaced exactly as the method demands. The server cannot
see that A's copy was stale — staleness lives in time, not in the request. Detecting it
needs extra machinery (version headers), which arrives in a later task.

### Your doubts

> **Doubt (on the common mistake "assuming the server merges `PUT` bodies — merging is
> `PATCH`'s job"):** "If a server quietly merges on `PUT`, it is breaking the contract
> other clients rely on."

Exactly right. `PUT`'s contract is one sentence: **after a successful `PUT`, the resource
equals the body that was sent.** Clients build two practical things on that sentence:

1. **Erasing by omission.** `PUT` has no "delete this field" syntax. The only way to
   remove a venue's city is to send the complete new version *without* a city. That works
   only if omitted really means gone.
2. **Knowing the state without re-fetching.** After a success reply, the client already
   knows exactly what the resource looks like — it is the body it just sent. No follow-up
   `GET` needed.

A quietly-merging server breaks both. Two simulated servers, same request; the client's
intent is "the venue went online-only, erase the city", said in `PUT`'s language by
sending the complete new resource with no city in it (compiled and run, Java 21):

```java
public class QuietMergeDemo {
    // The venue stored on the server. null plays the role of "this field is absent".
    static String storedName;
    static String storedCity;

    // Server 1 keeps PUT's contract: the body becomes the WHOLE resource.
    static void putThatReplaces(String bodyName, String bodyCity) {
        storedName = bodyName;
        storedCity = bodyCity;
    }

    // Server 2 quietly merges: any field missing from the body keeps its old value.
    static void putThatQuietlyMerges(String bodyName, String bodyCity) {
        if (bodyName != null) storedName = bodyName;
        if (bodyCity != null) storedCity = bodyCity;
    }

    static String venue() {
        return "{name=" + storedName + ", city=" + storedCity + "}";
    }

    public static void main(String[] args) {
        // The client sends the complete new resource, which simply has no city:
        // PUT /venue  {"name":"Big Hall"}

        storedName = "Old Hall"; storedCity = "Leeds";
        putThatReplaces("Big Hall", null);
        System.out.println("spec-keeping server after PUT : " + venue());

        storedName = "Old Hall"; storedCity = "Leeds";
        putThatQuietlyMerges("Big Hall", null);
        System.out.println("quietly-merging server after PUT: " + venue());
    }
}
```

Output:

```
spec-keeping server after PUT : {name=Big Hall, city=null}
quietly-merging server after PUT: {name=Big Hall, city=Leeds}
```

Both servers would answer `200 OK`, but on the merging one the erase **silently did not
happen**, and the client's belief about the resource is now wrong. That is the force of
"quietly": every reply is a success; the lie only surfaces later, as wrong data. The
damage runs in both directions:

- **Clients that follow the spec** try to erase a field by omitting it, are told
  "success", and the erase never happens. Correct code, corrupted data, no error.
- **Clients that learn the quirk** start sending partial `PUT` bodies because "it works
  here". The day the API is reimplemented by a server that keeps the contract, every one
  of those partial `PUT`s starts erasing fields.

One precision: quiet merging does **not** break `PUT`'s idempotency — merging the same
body five times ends in the same state as merging it once, so retries stay harmless. What
breaks is the *meaning*: a merging `PUT` is a `PATCH` answering to `PUT`'s name. The API
then offers two spellings of "change only the parts I mention" and no way at all to say
"replace the whole thing". The honest designs are exactly two: treat the body as the
complete replacement (`PUT`), or declare the body to be a list of changes (`PATCH`) — and
a server that cannot accept a `PUT` body as a full replacement should refuse it with a
`4xx`, never guess.

## Lesson 17 — A `GET` that changes things, broken on purpose

### What is it?

An endpoint that changes state but answers to `GET` — the toy app's
`GET /events/{id}/cancel`, which incremented a cancellation counter on every call. It was
run, watched doing damage, and then deleted, as the task instructs.

### Why do we need it?

`GET`'s promise is what lets the world's automation — prefetchers, link previews,
crawlers, health checks — call URLs freely. This lesson is the other side: what happens
when a developer breaks the promise. The difference between knowing a rule and having
watched its absence hurt someone.

### How does it work?

1. A developer exposes a state change as a `GET`, usually because a plain link is
   convenient — links, email buttons and bookmarks are all `GET`s.
2. The URL now looks, to every piece of software on Earth, like a harmless read.
3. Software reads it: browsers refresh and prefetch it, chat apps fetch it for preview
   cards, crawlers follow it, monitors probe it.
4. Every "look" executes the state change. Nobody clicked. Nothing errored.

### Simple example (run for real)

The endpoint that was in the toy app:

```java
private int cancellations = 0;

@GetMapping("/events/{id}/cancel")
String cancel(@PathVariable String id) {
    cancellations++;
    return "cancelled " + id + " (total cancellations: " + cancellations + ")";
}
```

Four calls, labelled with who would realistically make them:

```
=== the user clicks the cancel link ===
cancelled 7 (total cancellations: 1)
=== the user presses refresh ===
cancelled 7 (total cancellations: 2)
=== a chat app fetches the pasted link for a preview ===
cancelled 7 (total cancellations: 3)
=== a crawler follows the link ===
cancelled 7 (total cancellations: 4)
```

Then the endpoint was deleted from the app and the removal verified:

```
=== the cancel endpoint after removal ===
HTTP/1.1 404 
=== POST /events still works ===
[{"name":"still-here"}]
```

### Walkthrough

1. Call one was the only one a human intended.
2. Call two was a refresh — browsers re-send `GET`s on refresh without a second thought,
   *because `GET` promises to change nothing*.
3. Call three: a chat app built a preview card. A robot cancelled the event while making a
   thumbnail.
4. Call four: a crawler followed the link, as crawlers follow every link.
5. One intention, four executions. Every extra execution came from machinery *correctly*
   trusting a promise this endpoint was breaking. **This is why safe methods are a rule and
   not a style preference.** The fix: a cancellation changes state, so it must be a `POST`
   (or `DELETE`) — methods no prefetcher or previewer will ever touch.

Real-world precedent: in 2005 a browser add-on that prefetched links made users of real
web apps watch their data get deleted — those apps had exposed "delete" as `GET` links.
The add-on was behaving; the apps were lying.

### Where is it used?

In reverse — things that must never exist: cancel, delete, publish, pay, restart or logout
as `GET` links; one-click dashboard actions wired as links; any state change reachable by
pasting a URL.

### Common mistakes

- **"It's behind a login, so it's fine."** Prefetch and history-restore run *inside the
  logged-in browser*, carrying the user's cookies. Auth limits who triggers the damage to
  exactly your legitimate users.
- **"We'll tell users not to refresh."** The callers that matter are not users.
- **"It's just a counter."** The mechanism does not care what it mutates. Today a counter,
  tomorrow a refund.

### Summary

- A mutating `GET` turns every refresh, preview and crawl into an unintended write.
- One human intention became four executions, with no villain anywhere.
- Authentication does not repair the broken promise; the right method does.
- The endpoint is deleted; `POST /events` remains for the headers task.

### Practice questions

**Question 1.** An operations dashboard shows a link labelled "Restart server 3", wired as
`GET /servers/3/restart`. An engineer copies the link and pastes it into the team chat to
ask "should I run this?". Walk through what can happen next without anyone clicking
anything, name the promise the endpoint breaks, and state the one-line fix.

**Answer.** The moment the link lands in the chat, the chat application fetches the URL to
render a preview card — and that fetch *is* a `GET /servers/3/restart`, so server 3
restarts while the engineer is still typing the question. Other triggers need no click
either: a teammate's browser may prefetch the link, and anyone re-opening the chat page
may cause it to be fetched again. The endpoint breaks the safety promise: `GET` declares
"calling me changes nothing", and every piece of automation trusts that declaration. The
fix: make the action's method tell the truth — `POST /servers/3/restart`, triggered by a
button, never a bare link.

**Question 2.** A teammate defends a mutating `GET` endpoint: "`GET /orders/9/cancel`
requires a logged-in session, so random crawlers can't hit it — it's safe enough." Give
two concrete ways the order still gets cancelled with no human intending it, and explain
why requiring login does not repair the broken promise.

**Answer.** Way one: the logged-in user's own browser. A refresh of the cancel URL, a
prefetch of the link while the user hovers near it, or re-opening it from history — all
re-send the `GET` with the user's session attached, and the order cancels again with no
intent. Way two: software acting inside the user's session — a web chat open in the same
browser fetching a pasted link for a preview, or a browser extension that prefetches
pages, both carry the user's cookies. Login only restricts *whose* session the accidental
calls run in: your legitimate users'. The safety promise is about intent — "this call
changes nothing, so calling it needs no intent" — and authentication carries no
information about intent. Only the method does.

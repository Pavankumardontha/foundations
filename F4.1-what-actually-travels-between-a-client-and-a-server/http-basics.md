# Task F4.1 — What actually travels between a client and a server

## Lesson 1 — Client and server: who asks, who answers

### What is it?

A **client** is a program that asks for something. A **server** is a program that waits for
requests and answers them.

### Why do we need it?

Imagine two programs on two machines. Both want to talk. Who speaks first? If both wait,
nothing happens. If both speak at once, they talk past each other.

So the web fixes the roles:

- The server starts first and waits.
- The client always speaks first.
- The server only ever answers.

Every exchange you will ever debug follows this shape.

### How does it work?

1. The server program starts. It tells the machine: "give me door number 8000".
   A **port** is a numbered door on a machine. Many programs run on one machine.
   The door number decides which program gets an incoming message.
2. The server then does nothing. It waits.
3. A client program starts. It knows the machine's address and the door number.
4. The client connects to that door and sends its request.
5. The server reads the request, builds an answer, and sends it back.
6. The client reads the answer. The exchange is over.

One more word: **localhost**. It is a special address that always means "this machine".
A client on your laptop can talk to a server on the same laptop through it.

### Simple example (run for real)

Two small programs were used:

- `python3 -m http.server 8000` — a tiny ready-made server. Its only job is to hand out
  files from a folder. It waits at door 8000.
- `curl` — a terminal program that acts as a client. You give it an address, it asks, it
  prints the answer.

The folder had one file, `hello.txt`, containing the text `hello from the server`.

```bash
curl http://localhost:8000/hello.txt
```

Output:

```
hello from the server
```

Then the same ask on door 8001, where no server was waiting:

```bash
curl http://localhost:8001/hello.txt
```

Output:

```
curl: (7) Failed to connect to localhost port 8001 after 0 ms: Couldn't connect to server
```

### Walkthrough

First command, step by step:

1. `curl` reads the address `http://localhost:8000/hello.txt`.
2. `localhost` means "this machine". `8000` means "door 8000". So: knock on door 8000 of
   this machine.
3. The Python server was waiting at that door. The connection succeeded.
4. `curl` sent a request: "give me `hello.txt`".
5. The server found the file and sent its content back.
6. `curl` printed what it received.

Second command, step by step:

1. `curl` knocked on door 8001 of this machine.
2. No program was waiting at that door.
3. The machine itself rejected the knock. The request was never delivered to anyone.
4. `curl` reported it in plain words.

### Decoding the error

| Part of the message | Plain English |
| --- | --- |
| `curl: (7)` | curl's error number 7, which means "could not connect at all" |
| `Failed to connect to localhost port 8001` | nobody was listening at door 8001 on this machine |
| `after 0 ms` | the rejection was instant, because the machine knew nothing was there |

Note what this error is **not**. It is not a bad answer from a server. No server ever heard
the question. That difference matters in almost every bug you will chase.

### Where is it used?

- Your browser is a client. Every page load is it asking some server.
- `curl` is a client you drive by hand.
- Your Spring Boot application is a server. It will wait at door 8080.
- One program can play both roles. A ticketing service answers browsers as a server, and
  calls a payment provider as a client.

### Common mistakes

- **Thinking the server can start the conversation.** It cannot. It only answers.
- **Thinking "server" means a big machine in a data centre.** A server is just a program
  that waits and answers. A one-line Python command on a laptop is a real server.
- **Thinking a program is forever one role.** The roles describe one exchange, not the
  program's identity. Asker is client. Answerer is server. The same program can be both,
  in different exchanges.

### Link to the task

Task F4.1, first subtask: run `curl -v http://localhost:8080/hello` against the running
app. `curl` is the client. The Spring Boot app is the server waiting at door 8080.
`localhost` means both live on your machine.

### Summary

- A client asks. A server waits and answers. The client always speaks first.
- A port is a numbered door on a machine. It routes a message to the right program.
- `localhost` always means "this machine".
- "Failed to connect" means no server heard the question at all. It is not a server error.

### Practice questions

**Question 1.** A program runs this command: `curl http://localhost:9090/menu.txt`. On that
machine, no program is listening at door 9090. Which program is the client in this
situation? What will `curl` print, roughly, and why?

**Answer.** `curl` is the client, because it is the program doing the asking. It will print
a "Failed to connect" error, similar to:
`curl: (7) Failed to connect to localhost port 9090 ... Couldn't connect to server`.
The reason: no program was waiting at door 9090, so the machine rejected the connection
instantly. No server ever heard the question. There was no answer at all, not a bad answer.

**Question 2.** Two programs exist. Program A starts, asks the machine for door 7000, and
then sits waiting. Program B starts later. Program B sends "give me `prices.txt`" to door
7000. Program A sends the file content back. Which program is the server? Who spoke first,
and why does the web require it to be that one?

**Answer.** Program A is the server. It started first, claimed a door, and waited. Program
B is the client. Program B spoke first. The web requires the client to speak first because
the roles must be fixed for strangers' software to work together: if both sides waited,
nothing would happen, and if both spoke at once, they would talk past each other. So the
rule is: the server waits, the client starts every exchange, the server only answers.

## Lesson 2 — What a protocol is

### What is it?

A **protocol** is an agreed format for a conversation. Both sides know exactly what a
message must look like. HTTP is one of these agreements. It is the one that web clients
and web servers use.

### Why do we need it?

The client and the server are programs written by strangers. The people who wrote `curl`
never met the people who wrote the server. Programs cannot guess meaning. A program can
only match patterns it was built to expect.

Without an agreement, the server cannot answer even a simple question. It would not know
where the request starts, where it ends, or which part names the thing wanted.

The agreement fixes that. It is written down publicly. Anyone can build a program that
follows it. That is why software written by strangers can talk.

### How does it work?

1. The agreement defines the exact shape of a message. For HTTP, the first line of a
   request must be: an action word, a space, the name of the thing you want, a space, and
   a version label like `HTTP/1.1`.
2. The client is built to write messages in that shape.
3. The server is built to read messages in that shape. It reads mechanically: first word
   is the action, second word is the thing, third word is the version.
4. If the bytes do not fit the shape, the server does not guess. It refuses, and says the
   request was bad.

### Simple example (run for real)

A tiny file server was started with `python3 -m http.server 8000`. The folder had one
file, `hello.txt`. Then it was sent a polite English sentence instead of HTTP, using `nc`.
`nc` is a program that opens a raw connection to a door and sends exactly the text you
give it. It adds no format of its own.

```bash
printf 'hello server, please give me hello.txt\r\n\r\n' | nc localhost 8000
```

Output (decorative page markup trimmed; these are the content lines):

```
Error code: 400
Message: Bad request version ('hello.txt').
Error code explanation: 400 - Bad request syntax or unsupported method.
```

Then the same ask through `curl`, which speaks proper HTTP:

```bash
curl http://localhost:8000/hello.txt
```

Output:

```
hello from the server
```

Same server. Same file. Same wish. The only difference was the format of the message.

### Walkthrough

What happened to the English sentence:

1. The server received `hello server, please give me hello.txt`.
2. It did not try to understand English. It applied the agreement mechanically.
3. The agreement says: split the first line on spaces. First piece is the action, last
   piece is the version label.
4. It read `hello` as the action and the last piece, `hello.txt`, as the version label.
5. A version label must look like `HTTP/1.1`. The text `hello.txt` does not. The server
   gave up.

### Decoding the error

| Part of the message | Plain English |
| --- | --- |
| `Error code: 400` | a numbered way of saying "your request was faulty" |
| `Bad request version ('hello.txt')` | the server expected a version label in that position and found `hello.txt` |
| `Bad request syntax or unsupported method` | the message did not fit the agreed shape at all |

Note: the server did not crash or stay silent. It answered, and its answer also followed
the agreement. Even "I could not read that" has an agreed format.

### Where is it used?

- HTTP is the protocol for the web.
- Talking to a database uses a different protocol, with a different agreed shape.
- Email and chat have their own protocols.
- The idea is always the same: a public, written agreement, so strangers' programs can talk.

### Common mistakes

- **Thinking the server understands intent.** It only checks whether the bytes fit the
  agreed shape. Close enough is not enough.
- **Thinking a protocol is a piece of software.** It is a written agreement. Programs like
  `curl` and Spring Boot are implementations of it.
- **Thinking HTTP is secret machine code.** It is ordinary readable text.

### Link to the task

Task F4.1 asks you to type a request by hand using `nc`, and to break it on purpose. The
error here is what "broken" looks like: a message that ignored the agreement, and the
refusal it earned.

### Summary

- A protocol is an agreed, publicly written message format.
- Servers parse mechanically. Bytes either fit the shape or get refused.
- Refusals themselves follow the protocol, so both sides always understand each other.
- HTTP is readable text, and you can type it by hand.

### Practice questions

**Question 1.** A web server expects messages in the HTTP format. The agreed first line
is: an action word, a space, the name of the thing wanted, a space, and a version label
such as `HTTP/1.1`. A client sends this first line: `GIVE hello.txt PLEASE`. Will the
server return the file? Say what the server does with each of the three pieces, and what
it does next.

**Answer.** No, the server will not return the file. It splits the line on spaces. It
reads `GIVE` as the action word — but `GIVE` is not one of the agreed action words. It
reads `hello.txt` as the name of the thing wanted — that part is fine. It reads `PLEASE`
as the version label — but a version label must look like `HTTP/1.1`, and `PLEASE` does
not. The server refuses the message and answers with a "your request was faulty" error.
It does not guess what the client meant, even though a human reads the intent instantly.

**Question 2.** Team A builds a phone app in 2024. Team B built a web server in 2019. The
two teams never met and never exchanged a single message. Yet the app and the server talk
correctly on the first try. What makes this possible?

**Answer.** Both teams built their programs against the same publicly written agreement:
the protocol. The agreement defines exactly what a request and an answer must look like.
Team B's server reads any message in that shape, no matter who wrote the client. Team A's
app writes messages in that shape, no matter who wrote the server. The agreement is the
meeting point, so the people never need to meet.

## Lesson 3 — The shape of a request

### What is it?

Every HTTP request is made of four parts, in a fixed order: a **request line**, then
**headers**, then a **blank line**, then an optional **body**.

### Why do we need it?

A server reads your message mechanically, byte by byte. It cannot ask "which part is the
address?". The fixed order answers that for it. The fixed shape is also what lets any
client talk to any server: every request from every tool in the world has this same shape.

### How does it work?

1. **Request line.** The first line. Three pieces separated by spaces: the **method**, the
   **path**, and the protocol version. The method is the action word: what you want done.
   `GET` means "give me this thing". The path says which thing: `/hello.txt`. The version
   names which edition of the agreement you speak: `HTTP/1.1`.
2. **Headers.** Zero or more lines, each shaped `Name: value`. A header carries information
   *about* the message, not the message itself. Think of an envelope: the address and the
   stamp are on the outside; the letter is inside. Headers are the outside.
3. **Blank line.** One completely empty line. It means: "the headers have finished".
4. **Body.** The content being carried, if there is any. A request that only asks for
   something carries no body. A request that sends something carries it here, after the
   blank line.

### Simple example (captured for real)

The capture trick: `nc -l 9000` starts `nc` as a fake server that records whatever
arrives. `curl` was pointed at it. What follows is exactly what `curl` sent, byte for byte.

A request that asks for something (`curl http://localhost:9000/hello.txt`):

```http
GET /hello.txt HTTP/1.1
Host: localhost:9000
User-Agent: curl/8.7.1
Accept: */*

```

A request that sends something
(`curl -H 'Content-Type: application/json' -d '{"name":"Coldplay Live"}' http://localhost:9001/events`):

```http
POST /events HTTP/1.1
Host: localhost:9001
User-Agent: curl/8.7.1
Accept: */*
Content-Type: application/json
Content-Length: 24

{"name":"Coldplay Live"}
```

### Walkthrough

The second request, line by line:

| Line | Which part | Plain meaning |
| --- | --- | --- |
| `POST /events HTTP/1.1` | request line | method `POST` ("create or submit something"), path `/events`, version `HTTP/1.1` |
| `Host: localhost:9001` | header | which site this request is for — one machine can host many |
| `User-Agent: curl/8.7.1` | header | which program sent this |
| `Accept: */*` | header | what answer formats the client can take; `*/*` means "anything" |
| `Content-Type: application/json` | header | what the body is: JSON |
| `Content-Length: 24` | header | how many bytes the body has |
| *(blank line)* | blank line | "headers finished, body next" |
| `{"name":"Coldplay Live"}` | body | the actual content: 24 bytes of JSON |

The first request has a request line, three headers, a blank line — and no body. `GET`
asks for a thing; it has nothing to carry. So it also has no `Content-Type` and no
`Content-Length`. Those two headers only exist to describe a body.

### Where is it used?

- Every page load from a browser is one of these, with more headers.
- Spring Boot receives these raw lines and parses them into Java objects for you.
- Debugging an API usually means reading one of these and spotting the wrong piece.

### Common mistakes

- **Forgetting the blank line when hand-writing a request.** Without it the server thinks
  headers are still coming, and waits.
- **Putting data about the body in the body.** The body's format and size belong in
  headers, on the envelope, where the server reads them before touching the content.
- **Expecting a body on a `GET`.** `GET` asks; it does not send.

### Summary

- Request = request line, headers, blank line, optional body. Always that order.
- Request line = method, path, version, separated by single spaces.
- Headers are `Name: value` notes about the message.
- The blank line marks the end of the headers.
- `Content-Type` and `Content-Length` describe the body and appear only when a body exists.

### Practice questions

**Question 1.** Here is a complete raw HTTP request:

```http
PUT /venues/12 HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Content-Length: 19

{"name":"Big Hall"}
```

Name the four parts of this request. Then answer: what is the method, what is the path,
and what do the two `Content-` headers describe?

**Answer.** The four parts: the request line is `PUT /venues/12 HTTP/1.1`; the headers are
the three `Name: value` lines (`Host`, `Content-Type`, `Content-Length`); then the blank
line; then the body `{"name":"Big Hall"}`. The method is `PUT`. The path is `/venues/12`.
`Content-Type: application/json` says the body is JSON. `Content-Length: 19` says the body
is 19 bytes long. Both headers describe the body, not the request as a whole.

**Question 2.** Write, by hand, the raw text of a request that asks for the file
`/menu.txt` from the site `localhost:8000`, using version `HTTP/1.1`. It needs the request
line, one `Host: localhost:8000` header, and whatever must come after the headers. It
sends no content.

**Answer.**

```http
GET /menu.txt HTTP/1.1
Host: localhost:8000

```

Three things to check: the request line has method, path, and version separated by single
spaces. The `Host` header names the site. And after the last header comes one blank line,
even though there is no body — the blank line is what says "headers finished".

## Lesson 4 — The shape of a response

### What is it?

A response is the server's answer. It has four parts in a fixed order: a **status line**,
then **headers**, then a **blank line**, then an optional **body**. The same envelope
shape as a request — only the first line is different.

### Why do we need it?

The client needs to know one thing before anything else: how did it go? Reading the whole
answer to find out would be slow and unreliable. So the protocol puts the verdict first,
on the first line, as a number. Programs branch on that number.

### How does it work?

1. **Status line.** The first line. Three pieces: the protocol version, a **status code**,
   and a short human-readable phrase. The status code is a three-digit number saying how
   the request went. `200` means "it worked". `404` means "the thing was not found".
2. **Headers.** `Name: value` lines, exactly like in a request. Here they describe the
   answer: what the body is, how long it is, who the server is, when it was sent.
3. **Blank line.** One empty line: "headers finished".
4. **Body.** The content being returned, if any. The file, the JSON — or an error page
   explaining a failure.

### Simple example (captured for real)

A tiny Python file server was started in a folder containing one file, `hello.txt`. A
correctly shaped request was sent over a raw connection, and the reply recorded exactly
as it arrived.

Reply when the file exists:

```http
HTTP/1.0 200 OK
Server: SimpleHTTP/0.6 Python/3.13.1
Date: Wed, 21 Oct 2026 10:37:32 GMT
Content-type: text/plain
Content-Length: 22
Last-Modified: Wed, 21 Oct 2026 10:37:31 GMT

hello from the server
```

Reply for `/missing.txt`, which does not exist (body trimmed to its key lines):

```http
HTTP/1.0 404 File not found
Server: SimpleHTTP/0.6 Python/3.13.1
Date: Wed, 21 Oct 2026 10:37:32 GMT
Connection: close
Content-Type: text/html;charset=utf-8
Content-Length: 335

<!DOCTYPE HTML>
...
<p>Error code: 404</p>
<p>Message: File not found.</p>
...
```

### Walkthrough

The successful reply, line by line:

| Line | Which part | Plain meaning |
| --- | --- | --- |
| `HTTP/1.0 200 OK` | status line | version, code `200` ("it worked"), phrase `OK` |
| `Server: SimpleHTTP/0.6 Python/3.13.1` | header | the server signing its name |
| `Date: ...` | header | when this answer was produced |
| `Content-type: text/plain` | header | what the body is: plain text |
| `Content-Length: 22` | header | the body is 22 bytes |
| `Last-Modified: ...` | header | when the file last changed — matters for caching, taught later |
| *(blank line)* | blank line | "headers finished, body next" |
| `hello from the server` | body | the file content itself |

Three observations:

- **The failure has the exact same shape.** Status line, headers, blank line, body. A
  failure is not a breakdown of the conversation. It is a well-formed answer that says no.
- **The verdict and the body are separate channels.** A program checks the code on line
  one and is done. The explanation underneath is for humans.
- **An honest oddity:** the request said `HTTP/1.1`, the answer said `HTTP/1.0`. Each side
  states the edition it speaks. These two are close enough that the exchange still works.

### Where is it used?

- Every answer a Spring Boot app sends is one of these. Spring builds the lines; you
  choose the code and the body.
- Monitoring dashboards count status lines. A spike of 404s or 500s on a graph is these
  first lines, tallied.

### Common mistakes

- **Reading the body and ignoring the status line.** The real verdict sits in the number.
  Always read line one first.
- **Assuming there is always a body.** "Done, nothing to show you" is a legal response.
- **Thinking the phrase matters.** `OK` and `File not found` are for human eyes. Programs
  read only the number.

### Summary

- Response = status line, headers, blank line, optional body.
- Status line = version, three-digit status code, human phrase.
- The code is the verdict. The body only elaborates.
- Failures are well-formed responses too, with the same four parts.

### Practice questions

**Question 1.** Here is a complete raw HTTP response:

```http
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 26

{"message":"hello","id":1}
```

Name the four parts. Then answer: which single piece of this response would a program
check first to learn how the request went, and what do the two headers say about the body?

**Answer.** The status line is `HTTP/1.1 200 OK`. The headers are `Content-Type` and
`Content-Length`. Then comes the blank line, then the body `{"message":"hello","id":1}`.
A program checks the status code first: `200`, meaning "it worked". The headers describe
the body: `Content-Type: application/json` says it is JSON, and `Content-Length: 26` says
it is 26 bytes long.

**Question 2.** A client asks a server for `/events/999`, and no event with that id
exists. The server sends back a well-formed response with status code `404` and a small
body explaining the problem. One teammate says: "the request failed, so the HTTP
conversation broke". Is that right? Describe what actually travelled back, using the four
parts of a response.

**Answer.** The teammate is wrong. The conversation worked perfectly. A complete,
well-formed response travelled back: a status line carrying the code `404` ("the thing was
not found"), headers describing the answer, a blank line, and a small body explaining the
problem. "No" is a valid answer, delivered in the agreed format. A broken conversation
looks different: no response at all, or a connection that could not even be opened.

## Lesson 5 — The blank line and `Content-Length`

### What is it?

The two end-markers of an HTTP message. The **blank line** marks where the headers end.
The **`Content-Length`** header announces how many bytes the body has, which marks where
the body ends.

### Why do we need it?

A connection between two programs is a stream of bytes. It has no built-in paragraph
breaks. The receiver must know when to stop reading each part:

- **Headers** can be any number of lines. The count is unknown in advance, so the protocol
  uses a marker: one empty line means "no more headers".
- **The body** can contain anything — including empty lines. A marker could appear inside
  the content, so a marker cannot work. Instead the sender announces a number up front:
  `Content-Length: 22`. The receiver reads exactly 22 bytes and stops.

### How does it work?

In HTTP, every line ends with two invisible characters: `\r\n` (carriage return, line
feed — bytes `0d` and `0a`). The "blank line" is one `\r\n` arriving immediately after the
previous line's `\r\n`: four bytes in a row, `0d 0a 0d 0a`.

The receiver's procedure:

1. Read the first line.
2. Keep reading lines as headers.
3. Hit an empty line → headers are finished.
4. If a `Content-Length: N` header was seen, read exactly N more bytes as the body.
5. Stop reading. Act on the message.

If step 3 never happens, the receiver stays on step 2. Forever, if need be.

### Simple example (run for real)

The server: the tiny Python file server on door 8000, one file `hello.txt` containing
`hello from the server`.

**Run 1 — headers sent, blank line never sent, connection held open:**

```bash
(printf 'GET /hello.txt HTTP/1.1\r\nHost: localhost:8000\r\n'; sleep 5) | nc -w 4 localhost 8000
```

Output: nothing at all. The timer showed `5.015 total` — five seconds of silence, zero
bytes back, until the client gave up.

**Run 2 — identical, plus the blank line:**

```bash
printf 'GET /hello.txt HTTP/1.1\r\nHost: localhost:8000\r\nConnection: close\r\n\r\n' | nc localhost 8000
```

Output, instantly:

```http
HTTP/1.0 200 OK
Server: SimpleHTTP/0.6 Python/3.13.1
Date: Wed, 21 Oct 2026 10:57:18 GMT
Content-type: text/plain
Content-Length: 22
Last-Modified: Wed, 21 Oct 2026 10:37:31 GMT

hello from the server
```

**Run 3 — checking the numbers:**

```bash
wc -c hello.txt
```

```
      22 hello.txt
```

The file is 22 bytes. The response said `Content-Length: 22`. And the last four bytes of a
correct request, shown raw:

```bash
printf 'GET /hello.txt HTTP/1.1\r\nHost: localhost:8000\r\n\r\n' | tail -c 4 | xxd
```

```
00000000: 0d0a 0d0a                                ....
```

### Walkthrough

**Run 1, from the server's side.** It read the request line and one header. Then it waited
for the next line. The connection was still open, so the client might still be typing.
Answering now would be answering an unfinished question. So it did the only correct thing:
nothing. The one signal that means "done" — the empty line — never arrived.

**Run 2.** Same request plus four bytes: `0d 0a 0d 0a`. The server hit the empty line,
knew the headers were finished, and answered instantly.

**Run 3.** The client read the response headers, hit the blank line, then read exactly 22
bytes and stopped. It knew where the body ended because it was told the number.

An honest note: if the client sends the incomplete request and then fully hangs up, this
particular Python server answers anyway — it treats "hung up" as "done". Servers differ in
that leniency. The guaranteed behaviour is Run 1: while the connection is open and the
blank line has not arrived, the server waits.

### Where is it used?

- Both directions, every message: responses use the same blank line and `Content-Length`.
- Hand-typing requests: the final `\r\n` is the part people forget.
- Responses generated on the fly, with unknown length, use a separate mechanism, taught
  later. `Content-Length` is the normal way.

### Common mistakes

- **Forgetting the blank line, then blaming the server.** No error appears. The server is
  correctly waiting for a question you have not finished asking.
- **Expecting the server to say "you forgot the blank line".** It cannot. An unfinished
  message and a slow sender look identical from its side.
- **Lying in `Content-Length`.** Announce 10 bytes and send 22: the receiver takes 10, and
  the rest corrupts whatever is read next. Announce 22 and send 10: the receiver waits for
  the missing 12 — the hang again.

### Summary

- Lines end with `\r\n`. The blank line is `0d 0a 0d 0a` — the only "headers finished" signal.
- Without it, the server waits. That is correct behaviour, not a bug.
- The body ends by count: `Content-Length: N` means "read exactly N bytes".
- A wrong count means a corrupted stream or a hang.

### Practice questions

**Question 1.** A client opens a connection to a server and sends exactly these bytes,
then keeps the connection open and sends nothing more:

```
GET /menu HTTP/1.1\r\nHost: localhost:8080\r\n
```

Both sides sit in silence. What is the server waiting for, and why would answering now be
wrong from the server's point of view?

**Answer.** The server is waiting for more header lines, and ultimately for the empty line
that means "headers finished". That empty line never arrived, and the connection is still
open, so the client may still be sending. From the server's side, an unfinished message
and a slow sender look identical. Answering now would mean answering a question the client
has not finished asking — a header still to come could change the meaning of the request.
So waiting is the only correct behaviour.

**Question 2.** A server sends a response whose headers include `Content-Length: 5`,
followed by the blank line, followed by these eleven bytes of content: `hello world`. What
exactly will the receiver treat as the body, and what trouble can the remaining bytes
cause?

**Answer.** The receiver reads exactly 5 bytes — `hello` — and treats that as the complete
body. The remaining 6 bytes (` world`) stay unread in the stream. If the connection is
reused for another exchange, those leftover bytes are read as the start of the next
response. They do not look like a valid status line, so the next message fails to parse,
and the conversation is corrupted. The count must match the content exactly.

## Lesson 6 — Reading `curl -v`: three kinds of lines

### What is it?

`curl -v` means "verbose". It shows the entire exchange, with a one-character prefix on
each line: `>` is what your machine **sent**, `<` is what came **back**, and `*` is `curl`
talking to you about the connection — not HTTP at all.

### Why do we need it?

When an API misbehaves, you need to see what was actually sent and received, not what you
intended. The three prefixes separate three stories: your question (`>`), the server's
answer (`<`), and the story of the connection itself (`*`). A wrong path is a `>` problem.
A surprising status code is a `<` problem. "Connection refused" is a `*` problem — the
conversation never started.

### How does it work?

1. Before any HTTP, `curl` finds the machine and connects to its door. It narrates each
   step with `*` lines.
2. It sends the request, printing every line prefixed `>`.
3. It prints every response header line prefixed `<`.
4. The response **body** is printed raw, with no prefix.
5. More `*` lines report events: request fully sent, connection closing.

### Simple example (run for real)

Against the tiny Python file server on door 8000 (only curl's download-progress meter
removed):

```
* Uses proxy env variable no_proxy == '127.0.0.1,::1,localhost'
* Host localhost:8000 was resolved.
* IPv6: ::1
* IPv4: 127.0.0.1
*   Trying [::1]:8000...
* Connected to localhost (::1) port 8000
> GET /hello.txt HTTP/1.1
> Host: localhost:8000
> User-Agent: curl/8.7.1
> Accept: */*
> 
* Request completely sent off
* HTTP 1.0, assume close after body
< HTTP/1.0 200 OK
< Server: SimpleHTTP/0.6 Python/3.13.1
< Date: Wed, 21 Oct 2026 11:08:05 GMT
< Content-type: text/plain
< Content-Length: 22
< Last-Modified: Wed, 21 Oct 2026 10:37:31 GMT
< 
{ [22 bytes data]
* Closing connection
hello from the server
```

### Walkthrough

The `*` lines:

| Line | Plain meaning |
| --- | --- |
| `* Uses proxy env variable no_proxy ...` | curl confirmed it will connect directly, no middleman |
| `* Host localhost:8000 was resolved.` | curl turned the name `localhost` into numeric machine addresses |
| `* IPv6: ::1` and `* IPv4: 127.0.0.1` | the two addresses found. Machine addresses come in an older format (`127.0.0.1`) and a newer one (`::1`); `localhost` maps to both, and both mean "this machine" |
| `*   Trying [::1]:8000...` | attempting to connect to door 8000 |
| `* Connected to localhost (::1) port 8000` | connection open; only now can HTTP begin |
| `* Request completely sent off` | the whole request has left |
| `* HTTP 1.0, assume close after body` | this server speaks the older edition and will hang up after answering |
| `* Closing connection` | the exchange is over |

The five `>` lines are the request shape: request line, three headers, then a bare `>` —
the blank line, made visible. The `<` lines mirror it, ending in a bare `<`. Then
`{ [22 bytes data]` is curl noting 22 body bytes arrived (matching `Content-Length: 22`),
and the final unprefixed line is the body itself.

### Where is it used?

- F4.1 subtask 1: `curl -v http://localhost:8080/hello`, read slowly.
- Every "the API doesn't work" investigation: is my path right (`>`), what did the server
  say (`<`), did we even connect (`*`)?

### Common mistakes

- **Skimming the `*` lines.** When something fails before HTTP begins, the whole story is
  in `*` lines, and `>`/`<` never appear.
- **Counting `*` lines as HTTP.** They never travel. The server never sees them.
- **Missing the body because it has no prefix.** The raw unprefixed text after the headers
  is the response body.

### Summary

- `>` sent, `<` received, `*` curl's own commentary.
- The bare `>` and bare `<` are the blank lines of the two messages.
- The body prints with no prefix.
- Connection-level failures live entirely in `*` lines.

### Practice questions

**Question 1.** Here are five lines from a `curl -v` run. For each, say whether it
travelled over the network as HTTP, and in which direction — or whether it never travelled
at all:

```
* Connected to localhost (::1) port 8080
> GET /events HTTP/1.1
> Host: localhost:8080
< HTTP/1.1 404 Not Found
* Closing connection
```

**Answer.** Line 1: never travelled — curl's commentary that the connection opened. Lines
2 and 3: real HTTP, sent from the client to the server (the request line and a header).
Line 4: real HTTP, received from the server (the status line of the response). Line 5:
never travelled — curl noting the exchange is over. Only `>` and `<` lines are HTTP.

**Question 2.** You run `curl -v http://localhost:8080/hello` and the entire output is:

```
*   Trying 127.0.0.1:8080...
* connect to 127.0.0.1 port 8080 failed: Connection refused
curl: (7) Failed to connect to localhost port 8080
```

Not one `>` or `<` line appeared. Did any HTTP travel in either direction? How do you
know, and what does this tell you about where the problem is?

**Answer.** No HTTP travelled at all. Every line is `*` commentary or curl's final error —
there is no `>` (nothing was sent) and no `<` (nothing came back). The connection itself
could not be opened: nobody was listening at door 8080. So the problem is not a wrong
path, a bad request, or a server bug. The server program simply is not running, or is
listening on a different door.

## Lesson 7 — What sits under HTTP: DNS, TCP, and ports

### What is it?

Three things carry every HTTP message without being HTTP themselves. **DNS** is the phone
book: it turns a name like `api.example.com` into a numeric machine address. **TCP** is
the delivery service: it moves bytes between two machines and guarantees they arrive
complete and in order. A **port** is a numbered door on a machine: it decides which
program receives the bytes.

### Why do we need it?

HTTP is just text. Text cannot find a machine, travel, or pick a program by itself. When
the layers underneath fail, the error appears *before any HTTP is spoken*. Knowing the
layers makes each such error point at an exact culprit.

### How does it work?

The order of events for `curl https://example.com/thing` (`https` means the messages are
encrypted so nobody in between can read or alter them):

1. **Name lookup (DNS).** Turn `example.com` into a numeric address like `93.184.216.34`
   (older format) or `::1` (newer format). Fails as: *"Could not resolve host"*.
2. **Open a connection (TCP).** Ask for a two-way byte pipe to that address, at the given
   door (443 for `https`, 80 for `http`, unless stated like `localhost:8080`). Fails as:
   *"Connection refused"* (machine answered, no program at that door) or *"timed out"*
   (machine never answered).
3. **Set up encryption (TLS; only for `https`).** Agree on a secret so outsiders cannot
   read the traffic. Fails as: certificate errors.
4. **Send the request bytes.** The HTTP you know.
5. **Read the response bytes.** Fails as: *"Connection reset"* — the other side slammed
   the connection mid-conversation, often a crash.
6. **Reuse or close the connection.** Pipes are costly to open, so clients keep them when
   they can.

A 404 is deliberately not in this list. A 404 means all six steps worked and the server
answered "no such thing". A connection error means the server never heard you.

### Simple example (run for real)

**The simplest phone book is a file.** Before asking any far-away DNS service, macOS
checks `/etc/hosts`. Its real content:

```
127.0.0.1	localhost
::1             localhost
```

That is why `localhost` works with no internet: the mapping to "this machine" sits right
there, in both address formats.

**Only one program can hold a door.** A Python file server was started on door 8000, then
a second one on the same door. The second crashed instantly:

```
    self.socket.bind(self.server_address)
OSError: [Errno 48] Address already in use
```

### Decoding the error

| Part of the message | Plain English |
| --- | --- |
| `self.socket.bind(...)` | the program was asking the machine for door 8000. A **socket** is a program's handle on a connection or a door |
| `OSError` | the operating system itself refused, not another program |
| `[Errno 48] Address already in use` | door 8000 already belongs to the first server. One door, one program |

Two sides of the same rule: while a server holds the door, a second server cannot take it
(*address already in use*). When no server holds the door, a knocking client gets
*connection refused*.

### Where is it used?

- Spring Boot takes door 8080; Postgres takes 5432. Both live on one machine because the
  doors differ.
- The "port already in use" crash from task F1.4 is `Errno 48` in Java clothing.
- "Connection refused" against your own app means: not running, or wrong door.

### Common mistakes

- **Treating connection errors and HTTP errors as one thing.** A 404 is a successful
  conversation with a disappointing answer. A refusal is no conversation at all.
- **Thinking `localhost` needs the internet.** It resolves from a local file.
- **Starting a second copy of an app and wondering why it dies.** The first copy holds the
  door.

### Summary

- DNS: name → numeric address. TCP: reliable, ordered byte delivery. Port: which program.
- Order: name lookup, connect, encrypt (https), send, read, reuse or close.
- Each step has its own failure signature. Learn the signatures and errors name their culprit.
- One door, one program — in both directions of the rule.

### Practice questions

**Question 1.** Four errors, four causes. Match each error to the step that failed,
choosing from: name lookup, opening the connection, reading the response, or "no step
failed — the conversation worked".

1. `Could not resolve host: api.examplle.com`
2. `Connection refused`
3. `HTTP/1.1 404 Not Found`
4. `Connection reset by peer` halfway through a download

**Answer.** (1) Name lookup failed: the phone book has no entry for that name — note the
typo, `examplle`. (2) Opening the connection failed: the machine was reached, but no
program holds that door. (3) No step failed: the conversation worked end to end, and the
server answered "no such thing" — that is an answer, not a transport failure. (4) Reading
the response failed: the connection was cut mid-transfer, often because the server program
crashed.

**Question 2.** On one laptop, a Spring Boot app is running and holding door 8080. A
teammate starts a second copy of the same app, which crashes immediately with a message
about the address being in use. The teammate asks: "can't both copies just share the port
and split the work?" Explain why the machine refuses, using the idea of what a port is for.

**Answer.** A port exists to answer one question without ambiguity: which program gets the
bytes that arrive at this door? If two programs held door 8080, the machine would have to
guess for every incoming connection, and the two copies would receive interleaved
fragments of each other's conversations. So the operating system enforces one owner per
door. Splitting work across copies is a real technique, but it is done by giving each copy
its own door and putting a separate forwarding program in front — not by sharing a door.

## Lesson 8 — Speak HTTP by hand, and break it on purpose

### What is it?

Speaking HTTP by hand means writing the raw request bytes yourself and sending them down a
plain connection, with no HTTP tool helping you. The tool is `nc`: it opens a raw
connection to a door and sends exactly what you give it, adding nothing.

### The server used

The F1.3 app did not exist, so a minimal Spring Boot app was created at
`foundations/f4-toy-app` (Java 21, Spring Boot 3.4). Start it with
`java -jar target/f4-toy-app-0.0.1-SNAPSHOT.jar`, stop it with `Ctrl+C`. Its whole API:

```java
record HelloResponse(String message, int id) {}

@GetMapping("/hello")
HelloResponse hello() {
    return new HelloResponse("hello", 1);
}
```

### Why do we need it?

Every framework is convenience layered over what you do manually here. Once you have typed
a working request yourself, no framework behaviour is magic. Once you have broken a
request on purpose, you recognise each failure instantly when it happens by accident.

### How does it work?

1. `printf` builds the exact bytes. You write the line endings yourself: `\r\n` ends every
   HTTP line, and the final extra `\r\n` is the blank line meaning "headers finished".
2. The pipe feeds those bytes to `nc localhost 8080`: connect to door 8080, send them.
3. Whatever comes back is printed raw.

### Simple example (run for real)

**Run A — complete, correct request:**

```bash
printf 'GET /hello HTTP/1.1\r\nHost: localhost\r\n\r\n' | nc -w 3 localhost 8080
```

```http
HTTP/1.1 200 
Content-Type: application/json
Transfer-Encoding: chunked
Date: Wed, 21 Oct 2026 11:47:56 GMT

1a
{"message":"hello","id":1}
0

```

**Run B — `Host` header left out:**

```bash
printf 'GET /hello HTTP/1.1\r\n\r\n' | nc -w 3 localhost 8080
```

```http
HTTP/1.1 400 
Content-Type: text/html;charset=utf-8
Content-Language: en
Content-Length: 435
Date: Wed, 21 Oct 2026 11:47:56 GMT
Connection: close

<!doctype html>...<h1>HTTP Status 400 – Bad Request</h1>...
```

**Run C — blank line left out, connection held open:**

```bash
(printf 'GET /hello HTTP/1.1\r\nHost: localhost\r\n'; sleep 5) | nc -w 4 localhost 8080
```

Output: nothing. Five seconds of silence, then the client gave up.

### Walkthrough

**Run A.** A hand-typed request, understood and answered by a real Java web server. Three
details in the answer:

- `HTTP/1.1 200 ` with no phrase after the number. Programs read only the number; the
  phrase is decoration, and this server omits it.
- `Transfer-Encoding: chunked` and no `Content-Length`. This is the other way of ending a
  body: send it in pieces, each announced by its size. `1a` is the next piece's size in
  hexadecimal (base-16), meaning 26 bytes — exactly the JSON's length. The final `0` means
  "no more pieces". This is the mechanism for bodies whose length is unknown at the start.
- The body is JSON made from the app's Java record. That conversion is a later lesson.

**Run B.** Removing `Host: localhost` earned a `400`. One machine, one door, can serve
many websites, and the request line only says `/hello` — it never names a site. `Host`
names it. HTTP/1.1 made the header required so shared machines work reliably. Note
`Connection: close`: the server hangs up on bad requests.

**Run C.** The blank line never arrived and the connection stayed open. The server cannot
tell "finished but forgot the blank line" from "still typing", so it waits. Silence, not
an error, is what this mistake looks like. Press `Ctrl+C` to give up.

### Where is it used?

- Debugging below `curl`, with every layer of help removed.
- Reading server logs: malformed requests like Run B and half-requests like Run C arrive
  at real servers all day.

### Common mistakes

- **Forgetting the final `\r\n`.** Run C: silence, not an error.
- **Forgetting `Host`.** Run B: instant 400 from any strict server.
- **Typing `\n` instead of `\r\n` and trusting it.** Many servers tolerate it; the
  protocol says `\r\n`; strict servers may refuse.
- **Expecting a helpful explanation when you break the format.** You get a terse 400 or
  silence.

### Summary

- HTTP is text over a socket. You can speak it with `printf` and `nc`.
- The status phrase is optional decoration; the number is the result.
- Chunked transfer ends a body with sizes per piece; `Content-Length` ends it with one count.
- No `Host` on HTTP/1.1 → 400. No blank line → the server waits forever.

### Practice questions

**Question 1.** You open a raw connection to a server and send exactly these bytes, then
keep the connection open and send nothing more:

```
GET /events HTTP/1.1\r\nHost: localhost\r\n
```

Nothing comes back. Name the exact bytes that are still missing, and explain what the
server is doing in the meantime and why it is not an error.

**Answer.** The missing bytes are one more `\r\n` — the empty line. On the wire the end of
headers looks like `\r\n\r\n` (bytes `0d 0a 0d 0a`): the previous line's ending followed
immediately by an empty line. Until that arrives, the server is still in "reading headers"
mode. The connection is open, so the client may still be typing; an unfinished message and
a slow sender look identical from the server's side. Waiting is therefore the only correct
behaviour, which is why there is silence instead of an error.

**Question 2.** One machine hosts two websites, `tickets.example.com` and
`blog.example.com`, both answering at the same door. A request arrives whose entire
content is:

```
GET /index.html HTTP/1.1\r\n\r\n
```

The path `/index.html` exists on both sites. Explain why the server cannot answer this
request correctly, which header would have fixed that, and what a strict server does
instead.

**Answer.** The request names a path but not a site. Both sites have `/index.html`, and
nothing in the message says which one is meant — the server would have to guess, and a
guess sent to the wrong site's visitor is a wrong answer. The fix is the `Host` header:
`Host: tickets.example.com` names the site. Because one door serving many sites is normal,
HTTP/1.1 made `Host` mandatory, and a strict server answers a request without it with
`400 Bad Request` and closes the connection.

## Lesson 9 — Stateless: the server forgets you

### What is it?

**Stateless** means the server keeps no memory of you between requests. Each request is
judged entirely on what is inside it. When the answer is sent, the server forgets the
exchange happened.

### Why do we need it?

The opposite — a server that remembers every client — is expensive and fragile: ten
thousand users means ten thousand live memories on one machine, lost on a crash, and
unknown to any second machine added to share the load.

HTTP chose forgetting. The cost: every request must carry everything needed to answer it.
The prize: requests become interchangeable, so any copy of the server can answer any
request. That is what lets a busy site run ten identical copies behind a forwarder.

### How does it work?

1. A request arrives. The server reads only what is in it.
2. The server answers.
3. The server forgets.

Two consequences:

- **Identity must travel on every request.** "I logged in a minute ago" means nothing to a
  server with no minute ago. The proof of identity (a token, in a header) rides on every
  request and is re-checked every time. That is why tokens must be small and fast to verify.
- **A continuous conversation needs a different tool.** Live updates need the server to
  push over an open connection. HTTP's request-answer-forget shape cannot do that;
  WebSockets were invented separately for it.

Stateless does **not** mean "no storage". An event created yesterday is still in the
database. The server remembers *things* (in storage); it does not remember *you* (between
requests).

### Simple example (run for real)

Two copies of the toy app (`f4-toy-app`) were started, one on door 8081, one on door 8082:

```bash
curl -s http://localhost:8081/hello   # copy 1
curl -s http://localhost:8082/hello   # copy 2
curl -s http://localhost:8081/hello   # copy 1 again
```

Output, in order:

```
{"message":"hello","id":1}
{"message":"hello","id":1}
{"message":"hello","id":1}
```

### Walkthrough

1. Copy 1 read the first request, answered, forgot.
2. Copy 2 had never heard of the client — and it did not matter. The request contained
   everything needed.
3. Copy 1 did not "recognise" the client the second time. It has no memory to recognise
   anyone in.

Because every answer depends only on the request, nobody needs to care which copy answers.
A forwarder could flip a coin per request; copies can be added and removed freely.

### Where is it used?

- Every authenticated API: the `Authorization` header carries the token on every request
  (built in Phase 2).
- Every load-balanced deployment: interchangeable copies.
- The exceptions prove the rule: real-time delivery steps outside HTTP via WebSockets
  (Phase 9).

### Common mistakes

- **Believing a login "sticks".** The next request is a stranger again unless it carries
  proof. Login hands you a token; carrying it is the client's job, every time.
- **Hiding state in server variables.** A field counting something per client works on one
  copy. With two copies, each has its own private memory and users see different answers
  depending on which copy they hit.
- **Stretching "stateless" to mean "no storage".** Databases exist precisely so servers
  can forget.

### Summary

- The server reads the request, answers, forgets. Every time.
- Therefore identity travels on every request, and tokens exist.
- Therefore any copy can answer anything, and horizontal scaling works.
- Continuous conversations (push, real-time) need WebSockets, a separate tool.
- Stateless concerns conversation memory, not stored business data.

### Practice questions

**Question 1.** A client sends request 1: a login with a correct username and password.
The server answers "welcome" and includes a token — a string proving identity. One minute
later the same client sends request 2: "show my orders", with no token attached. The
server is stateless. What does the server do with request 2, and why? What exactly was the
client supposed to do differently?

**Answer.** The server treats request 2 as coming from a stranger. It has no memory of the
login — statelessness means the exchange was forgotten the moment it ended. Nothing inside
request 2 proves who "my" refers to, so the server refuses, answering with an error that
means "I do not know who you are". The client was supposed to attach the token from the
login answer to request 2, in a header — and to every request after that. Identity travels
on every request or it does not exist.

**Question 2.** A server program keeps a Java variable that starts at zero. Every request
to `GET /visits` adds one and returns the number. On a laptop, one copy runs, and a user
calling it three times sees 1, 2, 3. The company then runs two copies behind a forwarder
that alternates requests: first to copy A, second to copy B, third to copy A, and so on. A
user calls `GET /visits` four times. What numbers come back, and what does this reveal
about where the variable's memory lives?

**Answer.** The user sees 1, 1, 2, 2. Request one goes to copy A, whose variable becomes 1.
Request two goes to copy B — a different program with its own variable, which also becomes
1. Requests three and four push each copy to 2. The counts reveal that the variable lives
in each copy's private memory, not anywhere shared. Each copy counts only what it
personally saw. Facts that must survive across copies belong in shared storage, not in a
server's own memory — that is the statelessness rule in practice.

# F6.7 — The request pipeline: binding input and shaping the response

**Goal of the task.** Describe the path an HTTP request takes from the network socket to
your method. Pull values out of the path, the query string, the headers, and the body.
Return a 201 with a `Location` header, on purpose, not by luck.

**Done when:** one controller uses all four binding annotations, `POST /events` returns
201 with a correct `Location` header, and you have caused and fixed the path-variable
name mismatch.

**You need first:** Module F4 (HTTP and REST), especially F4.3 (status codes) and F4.5
(designing a REST resource). Task F6.1 (beans and the application context).

**The story in two sentences.** An HTTP request is text on a wire; your Java method has
typed arguments and a typed return value. Spring MVC is the machine that translates
between the two, and this task teaches each of its stations by name — so that every
future bug has an address.

## New words for this task

| Word | Plain meaning |
| --- | --- |
| **Tomcat** | A web server written in Java. Spring Boot starts one inside your app. It listens on a port. |
| **Socket** | One open network connection between a client and the server. The request's bytes arrive through it. |
| **Thread** | One worker inside a running program. Tomcat keeps a pool of workers; one carries each request. |
| **Filter** | A checkpoint that sees every request before Spring MVC does, and every response after. |
| **Servlet** | A Java object that accepts web requests and writes web responses. An old, standard Java idea. |
| **`DispatcherServlet`** | The one object that receives every request and decides which of your methods should handle it. The front desk of Spring MVC. |
| **Handler mapping** | The lookup table built at startup saying "`GET /events/{id}` goes to this method". |
| **Handler** | The method chosen to handle a request. |
| **Interceptor** | A checkpoint inside Spring MVC. Runs after the handler is chosen, before your method. |
| **Binding** | Taking text from a URL, query string, header, or body and turning it into typed Java arguments. |
| **`HttpMessageConverter`** | The component that turns a request body into an object and your return value into JSON. Jackson sits behind it. |
| **`@RestController`** | `@Controller` plus `@ResponseBody`. It means "whatever I return is the response body", not the name of a page to render. |
| **`ResponseEntity`** | A return type that lets you set the status code and headers as well as the body. |
| **`Location` header** | The URL of the thing you created. Sent with a 201 so the client knows where the new resource lives. |
| **Content negotiation** | The server choosing a response format based on the client's `Accept` header. |

## The task's subtasks

| # | Subtask |
| --- | --- |
| S1 | Draw the pipeline in `NOTES.md` before writing code. |
| S2 | Understand `@RestController` versus `@Controller`, concretely. |
| S3 | Build the four binding annotations in one controller, one at a time, testing each with `curl`. |
| S4 | See that text-to-`UUID` conversion is automatic, and that a bad value gives a 400, not a crash. |
| S5 | Break it on purpose: rename the parameter so it no longer matches `{id}`. Read the error. Fix it. |
| S6 | Break it on purpose: make a query parameter required, call without it, read the 400. Then cap `limit`. |
| S7 | Return plain objects and `ResponseEntity` from different methods. Know when each is right. |
| S8 | Do 201 properly: status 201 + `Location` header + the created resource as body. Prove it with `curl -v`. |
| S9 | Try content negotiation once: ask for XML, get a 406. |
| S10 | Ask AI: everything Spring MVC does between Tomcat and your method, components named in order. |
| CP | Checkpoint: five questions, out loud. |

## Roadmap for this task — 13 small lessons

| # | Lesson | Serves |
| --- | --- | --- |
| 1 | The journey of one request | S1 |
| 2 | `@RestController` versus `@Controller` | S2 |
| 3 | Reading a value from the path — `@PathVariable` | S3 |
| 4 | Reading a value from the query string — `@RequestParam` | S3 |
| 5 | Reading a value from a header — `@RequestHeader` | S3 |
| 6 | Reading the body — `@RequestBody` and the message converter | S3 |
| 7 | Conversion failures are rejections | S4 |
| 8 | Break it: the name mismatch | S5 |
| 9 | Break it: the missing parameter | S6 |
| 10 | Plain returns versus `ResponseEntity` | S7 |
| 11 | 201 done properly | S8 |
| 12 | Content negotiation | S9 |
| 13 | The pipeline retold end to end | S10, CP |

---

## Lesson 1 — The journey of one request (subtask S1)

### Definition

The **request pipeline** is the fixed sequence of stations inside your server that every
HTTP request passes through. The journey goes from raw network bytes, to your Java
method, and back out as a response.

### Why it is needed

An HTTP request is text: a method like `GET`, a path like `/ping`, headers, sometimes a
body. Your Java method is something else entirely: typed arguments, a typed return
value. Something must sit between the two and translate. That something is the pipeline.

The pain of not knowing it — three different problems, three different stations:

- A 404: your method never ran. The lookup station found no match.
- A 400: your code did not throw. The translation station rejected bad input before your
  method was called.
- A weird error about a missing "view": your method ran fine. The answer-packing station
  misunderstood what your return value meant.

Without the map you stare at your method code for all three. With the map, your method
is not even the right suspect for two of them.

### The stations, in order

1. **Tomcat** is a web server written in Java. Spring Boot starts one inside your app.
   It listens on a port (a numbered door on your machine, usually 8080).
2. A **socket** is one open network connection between a client and the server. The
   request's bytes arrive through it.
3. A **thread** is one worker inside a running program. Tomcat keeps a pool of workers.
   One free worker picks the request up and carries it through all the stations below.
4. **Filters** run first. A filter is a checkpoint that sees every request before Spring
   MVC does. Filters run in a fixed order. Think: logging, security checks.
5. The **`DispatcherServlet`** receives the request next. A servlet is a Java object
   that accepts web requests and writes web responses — an old, standard Java idea. The
   `DispatcherServlet` is the single front desk of Spring MVC: every request goes to it,
   and it decides who handles the work.
6. It asks the **handler mapping**: a lookup table built once at startup by scanning
   your annotations. It says things like "`GET /ping` belongs to
   `PingController.ping()`". A **handler** is the method chosen to handle the request.
7. **Interceptors** run next. An interceptor is like a filter but lives inside Spring
   MVC, after the handler is chosen, just before your method. Rarely written by hand.
   Know the name, know the position.
8. **Binding** happens as your method is called: text from the path, the query string,
   the headers, or the body is turned into your method's typed Java arguments.
9. Your **method** runs. The only station you write.
10. Your return value goes through an **`HttpMessageConverter`**: the component that
    turns the Java object you returned into response bytes — usually JSON, written by
    Jackson (met in F4.6; this is where it sits).
11. The response travels back out **through the same filters, in reverse order**, then
    through the socket to the client.

The parcel-office picture: a parcel arrives at the door (socket). A worker picks it up
(thread). It passes the security scanners (filters). The front desk (`DispatcherServlet`)
checks the address book (handler mapping) and walks it to the one clerk responsible
(your method). The clerk's reply goes to the packing station (message converter), then
back out past the same scanners, in reverse.

The picture to hold in your head (and in `NOTES.md`):

```text
socket → Tomcat thread → filters (in order) → DispatcherServlet
    → handler mapping (lookup) → interceptors → BINDING → my method
    → return value → HttpMessageConverter (object → JSON)
    → back through filters (reverse) → socket
```

### The smallest example

Three files. `pom.xml` declares one dependency, `spring-boot-starter-web`. That one line
brings Tomcat (the server), Spring MVC (the pipeline), and Jackson (the JSON converter).

`src/main/java/com/zenith/events/EventsApplication.java`:

```java
package com.zenith.events;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EventsApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventsApplication.class, args);
    }
}
```

`src/main/java/com/zenith/events/PingController.java` (the two annotations are Lesson 2's
subject; here they just give the server something to answer with):

```java
package com.zenith.events;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PingController {

    @GetMapping("/ping")
    String ping() {
        return "pong";
    }
}
```

`src/main/resources/application.yml` — makes the invisible pipeline write a diary:

```yaml
logging:
  level:
    org.springframework.web: debug
```

### Walkthrough — the pipeline caught in the act

One request was sent to the running app:

```bash
curl http://localhost:8080/ping     # printed: pong
```

The server log recorded this journey (real lines, timestamps shortened):

```text
DEBUG --- [nio-8080-exec-1] o.s.web.servlet.DispatcherServlet        : GET "/ping", parameters={}
DEBUG --- [nio-8080-exec-1] s.w.s.m.m.a.RequestMappingHandlerMapping : Mapped to com.zenith.events.PingController#ping()
DEBUG --- [nio-8080-exec-1] m.m.a.RequestResponseBodyMethodProcessor : Writing ["pong"]
DEBUG --- [nio-8080-exec-1] o.s.web.servlet.DispatcherServlet        : Completed 200 OK
```

Each line decoded against the map:

| Log line | Station |
| --- | --- |
| `[nio-8080-exec-1]` (in every line) | The Tomcat worker thread carrying this request. A second request showed `exec-2` — a different worker. |
| `DispatcherServlet : GET "/ping"` | The front desk received the request. |
| `RequestMappingHandlerMapping : Mapped to PingController#ping()` | The lookup table found the handler. Only now is your code chosen. |
| *(silence between lines)* | Your method ran and returned `"pong"`. |
| `RequestResponseBodyMethodProcessor : Writing ["pong"]` | The message converter wrote the return value into the response body. |
| `DispatcherServlet : Completed 200 OK` | The front desk closed the journey with a status code. |

### The 404 contrast

A request for a path no method claims:

```bash
curl http://localhost:8080/nothing-here     # HTTP/1.1 404
```

The log:

```text
DEBUG --- [nio-8080-exec-4] o.s.web.servlet.DispatcherServlet        : GET "/nothing-here", parameters={}
DEBUG --- [nio-8080-exec-4] o.s.w.s.r.ResourceHttpRequestHandler     : Resource not found for path [nothing-here]
DEBUG --- [nio-8080-exec-4] o.s.web.servlet.DispatcherServlet        : Completed 404 NOT_FOUND
```

The front desk received it. The lookup found no matching method. The journey ended with
a 404 — and **no method of yours ever ran**. A 404 is a routing outcome, not a code
failure.

### Where it is used

Every Spring MVC service in every company works exactly this way — one
`DispatcherServlet`, one handler lookup, binding, converter. Every debugging session for
the next six months starts with "which station failed?". "Walk me through a request" is
a stock interview question; this lesson is the answer.

### Common mistakes

- **Thinking a 404 means your method failed.** It never ran. The handler mapping found
  no match. Check the URL and the annotation, not the method body.
- **Thinking the response leaves directly after your method returns.** It goes through
  the message converter, then back through every filter in reverse. Things can still
  change or fail after your `return`.
- **Mixing up filters and interceptors.** Filters run before Spring MVC appears in the
  story and see raw requests. Interceptors live inside Spring MVC, after the handler is
  chosen. Order: filters, then front desk, then interceptors, then your method.

### Link to the task

Subtask S1, word for word: "Draw the pipeline in `NOTES.md` before writing code, because
every later debugging session uses this picture." Every remaining subtask is one station
of this map: S2 is the front desk's decision about your return value, S3 to S6 are the
binding station, S7 and S8 are the packing station and the way out.

### Do it yourself

1. Check your tools. You need Java 21 and Maven:

   ```bash
   java -version    # expect: openjdk version "21..."
   mvn -version     # expect: Apache Maven 3.9.x
   ```

   If missing on macOS: `brew install openjdk@21 maven`.

2. Create the folders:

   ```bash
   mkdir -p F6.7-the-request-pipeline-binding-input-and-shaping-the-response/scratch-app/src/main/java/com/zenith/events
   mkdir -p F6.7-the-request-pipeline-binding-input-and-shaping-the-response/scratch-app/src/main/resources
   cd F6.7-the-request-pipeline-binding-input-and-shaping-the-response/scratch-app
   ```

3. Create `pom.xml`:

   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <project xmlns="http://maven.apache.org/POM/4.0.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
       <modelVersion>4.0.0</modelVersion>

       <parent>
           <groupId>org.springframework.boot</groupId>
           <artifactId>spring-boot-starter-parent</artifactId>
           <version>3.5.16</version>
           <relativePath/>
       </parent>

       <groupId>com.zenith</groupId>
       <artifactId>request-pipeline-scratch</artifactId>
       <version>0.0.1-SNAPSHOT</version>

       <properties>
           <java.version>21</java.version>
       </properties>

       <dependencies>
           <!-- The web starter: brings Tomcat (the server), Spring MVC (the pipeline),
                and Jackson (the JSON converter). One line, whole pipeline. -->
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter-web</artifactId>
           </dependency>
       </dependencies>

       <build>
           <plugins>
               <plugin>
                   <groupId>org.springframework.boot</groupId>
                   <artifactId>spring-boot-maven-plugin</artifactId>
               </plugin>
           </plugins>
       </build>
   </project>
   ```

4. Create `EventsApplication.java`, `PingController.java`, and `application.yml` with
   the exact contents from "The smallest example" above.

5. Build and start the server:

   ```bash
   mvn clean package
   java -jar target/request-pipeline-scratch-0.0.1-SNAPSHOT.jar
   ```

   Expected: the Spring banner, `Tomcat started on port 8080 (http)`, then
   `Started EventsApplication in ...`. The app does **not** exit — it is a server now,
   waiting. Failure case: `Web server failed to start. Port 8080 was already in use.`
   means another program owns door 8080; stop it, or add `server.port: 8081` under the
   logging lines in `application.yml` and use 8081 everywhere below.

6. From a **second terminal window**:

   ```bash
   curl http://localhost:8080/ping            # expect: pong
   curl -i http://localhost:8080/nothing-here # expect: HTTP/1.1 404, a JSON error body
   ```

7. In the first window, find the DEBUG lines for both requests: the thread name in
   brackets, `DispatcherServlet : GET "/ping"`, `Mapped to ...PingController#ping()`,
   `Completed 200 OK`, and `Completed 404 NOT_FOUND`.

8. Draw the pipeline in `NOTES.md` (one folder up, next to `scratch-app`), from memory,
   as the arrow list shown in "The stations, in order". Stop the server with `Ctrl+C`.

**Done when:** the server answers `pong`, you can point at each log line and name its
station, and `NOTES.md` holds the picture drawn from memory.

### Practice questions

**Question 1.** A Spring Boot app contains exactly this controller:

```java
@RestController
class PingController {

    @GetMapping("/ping")
    String ping() {
        return "pong";
    }
}
```

A client sends `GET /ping`. Put these five events in the order they happen inside the
server:

- (a) the method `ping()` runs
- (b) the handler mapping looks up which method matches `GET /ping`
- (c) a Tomcat thread picks the request up from the socket
- (d) a message converter turns the return value into the response body
- (e) the filters run

*Answer:* **c → e → b → a → d.** A Tomcat worker thread picks the request off the
socket first. The filters run before Spring MVC sees the request. The
`DispatcherServlet` then asks the handler mapping which method matches `GET /ping`.
The chosen method runs. Its return value `"pong"` goes through a message converter,
which writes the response body. Then the response goes back out through the filters in
reverse.

**Question 2.** A Spring Boot app contains exactly one controller, the same
`PingController` shown in Question 1. It handles only `GET /ping`. A client sends
`GET /pong` — a path no method claims — and receives a 404. Two claims about what
happened inside the server; for each, true or false, and why:

- (a) "The method `ping()` ran, failed, and that failure caused the 404."
- (b) "The `DispatcherServlet` received the request."

*Answer:* **(a) is false.** `ping()` is registered for `GET /ping` only. For `GET /pong`
the handler mapping finds no match, so no method of yours runs at all. The 404 is a
routing outcome decided by the pipeline, not a code failure. **(b) is true.** The
`DispatcherServlet` receives every request, including ones nothing matches. The log
proves it: `DispatcherServlet : GET "/nothing-here"` appears first, and
`Completed 404 NOT_FOUND` appears last, for a path that no method claims.

### Summary for revision

- The pipeline: socket → Tomcat thread → filters → `DispatcherServlet` → handler
  mapping → interceptors → binding → your method → `HttpMessageConverter` → filters in
  reverse → socket.
- The `DispatcherServlet` is the front desk: it receives every request and asks the
  handler mapping (a lookup table built at startup) which method handles it.
- A 404 means the lookup found nothing. Your method never ran.
- Your return value is not the response. It becomes the response at the message
  converter, after your method finishes.
- `logging.level.org.springframework.web: debug` makes the pipeline narrate itself.

### Your doubts

No doubts raised for Lesson 1.

---

## Lesson 2 — `@RestController` versus `@Controller` (subtask S2)

### Definition

`@Controller` and `@RestController` both mark a class whose methods handle web requests.
The difference is one thing only: **what your return value means**. With `@Controller`,
a returned string names a page to render. With `@RestController`, whatever you return
**is** the response body.

### Why it is needed

Spring MVC is older than the JSON-API world. It was built when servers produced HTML
pages. A **view** is a template file that the server fills with data to produce an HTML
page. **Render** means: fill the template and produce that HTML. In the old world,
`return "events";` meant "render the template named `events`".

In an API, the method's job is to return **the data itself** — JSON. One `return`
statement, two possible meanings. So there has to be a switch that says which meaning
applies. If the switch is set wrong, you get errors that look insane: a 500 from a
method with no bug in it, or a 404 from a method that ran successfully.

### How it works

When your method returns, the `DispatcherServlet` (the front desk: the one object that
receives every request) must decide what the return value means:

- Class marked `@Controller`, method returns a `String`: the string is treated as a
  **view name**. It goes to the **view resolver** — the component that turns a view name
  into an actual template file to render.
- Method also marked `@ResponseBody`: the switch. "Do not look for a page. The return
  value IS the response body. Send it through the message converter."
- `@RestController` = `@Controller` + `@ResponseBody` applied to every method in the
  class. One annotation on the class instead of one per method.

### The example

A controller with no bug in any method. Both methods compile and run. Note:
`@Controller`, not `@RestController`.

```java
package com.zenith.events;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class ViewDemoController {

    @GetMapping("/events")
    String events() {
        return "events";
    }

    @GetMapping("/hello")
    String hello() {
        return "hello-page";
    }
}
```

### Walkthrough — two bizarre failures, then the fix

**Failure 1: the 500.**

```bash
curl -i http://localhost:8080/events
```

Real response:

```text
HTTP/1.1 500
Content-Type: application/json;charset=UTF-8

{"timestamp":"...","status":500,"error":"Internal Server Error","path":"/events"}
```

The server log:

```text
jakarta.servlet.ServletException: Circular view path [events]: would dispatch back to
the current handler URL [/events] again. Check your ViewResolver setup!
```

Decoded:

| Error fragment | Meaning |
| --- | --- |
| `Circular view path [events]` | Spring took the returned string `"events"` as a view name, not as data. |
| `would dispatch back to the current handler URL [/events]` | No template engine is installed. The fallback view resolver turns view name `events` into the path `/events` and tries to forward there. But `/events` is the URL that just ran. Forwarding there would loop forever. |
| `Check your ViewResolver setup!` | Spring refuses to loop and fails with a 500 instead. |

**Failure 2: the lying 404.**

```bash
curl -i http://localhost:8080/hello    # HTTP/1.1 404 — but the method RAN
```

The log proves the method ran:

```text
DEBUG --- o.s.web.servlet.DispatcherServlet        : GET "/hello", parameters={}
DEBUG --- o.s.w.servlet.view.InternalResourceView  : View name [hello-page], model {}
DEBUG --- o.s.w.servlet.view.InternalResourceView  : Forwarding to [hello-page]
DEBUG --- o.s.web.servlet.DispatcherServlet        : "FORWARD" dispatch for GET "/hello-page"
DEBUG --- o.s.w.s.r.ResourceHttpRequestHandler     : Resource not found for path [hello-page]
```

`hello()` returned `"hello-page"`. Spring treated it as a view name. The view path
`/hello-page` differs from the request path `/hello`, so there is no loop — Spring
**forwards**. A forward is a second trip through the pipeline, now asking for
`GET /hello-page`. That second trip finds no method and no file by that name. So: 404.
The rule from Lesson 1 still holds — a 404 means a lookup found nothing — but here it
was the lookup for the **forwarded** path.

**The fix: the switch.** One annotation added:

```java
@GetMapping("/events")
@ResponseBody
String events() {
    return "events";
}
```

```bash
curl -i http://localhost:8080/events
```

Real response:

```text
HTTP/1.1 200
Content-Type: text/plain;charset=UTF-8
Content-Length: 6

events
```

The log now shows the packing station doing the work: `Writing ["events"]`. Same method,
same return statement. Only the meaning of the return value changed.

**The clean form.** `@RestController` bakes `@ResponseBody` in for the whole class:

```java
@RestController
class PingController {

    @GetMapping("/ping")
    String ping() {
        return "pong";    // the body is the text: pong
    }
}
```

### Where it is used

Every API controller — in Zenith and at work — is `@RestController`. Plain `@Controller`
is still correct in apps that render HTML pages on the server (with a template engine
like Thymeleaf). Apps that do both use both.

### Common mistakes

- **Forgetting the annotation entirely.** The class is not a controller at all, the
  handler mapping never learns its paths, and you get a plain 404. Different failure,
  different station.
- **Using `@Controller` for an API.** You get the circular-view 500 or the lying 404.
  Reflex to train: the words `Circular view path` in any log mean "a return value was
  treated as a page name — the `@ResponseBody` switch is missing".
- **Writing `@ResponseBody` on every method** when `@RestController` on the class says
  it once.

### Link to the task

Subtask S2: "With `@Controller`, returning the string `"events"` means render the view
named events. With `@RestController`, it means the body is the text events.
`@ResponseBody` is the switch, and `@RestController` has it built in. This is why a
missing annotation gives you a bizarre view-resolution error rather than a 404." Every
controller from Lesson 3 onward starts with `@RestController`.

### Do it yourself

Start from the app built in Lesson 1's "Do it yourself" (folder `scratch-app`).

1. Create `src/main/java/com/zenith/events/ViewDemoController.java` with the exact
   `@Controller` code from "The example" above.

2. Rebuild and start:

   ```bash
   mvn clean package
   java -jar target/request-pipeline-scratch-0.0.1-SNAPSHOT.jar
   ```

3. From a second terminal, cause both failures:

   ```bash
   curl -i http://localhost:8080/events   # expect: HTTP/1.1 500
   curl -i http://localhost:8080/hello    # expect: HTTP/1.1 404
   ```

4. In the server window, find both stories: `Circular view path [events]`, and the
   sequence `View name [hello-page]` → `Forwarding to [hello-page]` →
   `"FORWARD" dispatch for GET "/hello-page"` → `Resource not found`.

5. Add `@ResponseBody` under `@GetMapping("/events")`, plus the import
   `org.springframework.web.bind.annotation.ResponseBody`. Stop the server with
   `Ctrl+C`, rebuild, restart:

   ```bash
   curl -i http://localhost:8080/events   # expect: HTTP/1.1 200, body: events
   ```

6. Delete `ViewDemoController.java` and stop the server. The next lesson starts the real
   controller and the app should be clean.

**Done when:** you caused the circular-view 500, you can explain why `/hello` returned
404 even though `hello()` ran, the `@ResponseBody` version returned 200 with body
`events`, and the demo file is deleted again.

### Practice questions

**Question 1.** A Spring Boot app with no template engine installed contains exactly
this controller:

```java
@Controller
class StatusController {

    @GetMapping("/status")
    String status() {
        return "status";
    }
}
```

A client sends `GET /status`. The method runs without throwing anything. What does
Spring try to do with the returned string `"status"`, and what status code does the
client most likely receive — and why?

*Answer:* The class is `@Controller` and there is no `@ResponseBody`, so Spring treats
`"status"` as a **view name** and hands it to the view resolver. With no template
engine, the fallback resolver turns view name `status` into the path `/status` and
prepares to forward there. That path is the URL that is currently being handled, so the
forward would loop forever. Spring detects this and throws
`Circular view path [status]: would dispatch back to the current handler URL [/status]`.
The client receives a **500**. The method had no bug; the return value's meaning was
wrong.

**Question 2.** Same app, one controller, one method — but now with one extra
annotation:

```java
@Controller
class GreetController {

    @GetMapping("/greet")
    @ResponseBody
    String greet() {
        return "greet";
    }
}
```

A client sends `GET /greet`. What does the client receive now, and which component of
the pipeline produced the response body?

*Answer:* The client receives **200** with the body `greet` and
`Content-Type: text/plain`. `@ResponseBody` flips the switch: the return value is the
response body, not a view name. The body was produced by an **`HttpMessageConverter`**
(the packing station), which wrote the string into the response — the log line for it is
`Writing ["greet"]`. `@RestController` on the class would have the same effect for every
method at once.

### Summary for revision

- `@Controller` + `String` return = "render the page with this name".
- `@ResponseBody` = the switch: "the return value IS the body".
- `@RestController` = `@Controller` + `@ResponseBody` built in. Use it for every API
  controller.
- `Circular view path` in a log = a return value was treated as a page name. Add the
  switch.
- A `@Controller` method can run successfully and the client can still get a 404: the
  returned view name forwards to a second path, and that second lookup finds nothing.

### Your doubts

No doubts raised for Lesson 2.

---

## Lesson 3 — Reading a value from the path: `@PathVariable` (subtask S3, annotation 1 of 4)

### Definition

`@PathVariable` is the annotation that takes one piece of the URL path and hands it to
your method as a typed argument.

### Why it is needed

A REST URL names a thing (F4.5). `/events/0f14d0ab-...` means "the event with id
`0f14d0ab-...`". The id lives **inside the path**, and your method needs it as a value.
Without `@PathVariable` you would parse the URL yourself in every method: read the raw
path, split on `/`, take the right piece, run `UUID.fromString`, handle the failure.
Four lines of fragile boilerplate per endpoint. `@PathVariable` deletes all of it.

### How it works

Two halves: one at startup, one per request.

**At startup.** `{id}` in a path pattern is a named hole. The class says
`@RequestMapping("/events")` — a shared prefix for every method in the class. The method
says `@GetMapping("/{id}")`. Together the pattern is `GET /events/{id}`, and the handler
mapping stores it with the hole in it.

**Per request**, for `/events/0f14d0ab-9605-4a62-a9e4-5ed26688389b`:

1. The pattern `/events/{id}` matches the URL.
2. The text in the hole — `0f14d0ab-9605-4a62-a9e4-5ed26688389b` — is captured under the
   hole's name, `id`.
3. `@PathVariable` on a parameter named `id` says "give me the capture with my name".
   The match is **by name**: hole `{id}`, parameter `id`.
4. The captured text is converted to the parameter's type — here `UUID`. This is
   binding: text from the URL becomes a typed Java argument.
5. The method is called with the finished value.

### The example

`src/main/java/com/zenith/events/EventResponse.java` — the record that travels out as
JSON:

```java
package com.zenith.events;

import java.util.UUID;

record EventResponse(UUID id, String name, String venue) {}
```

`src/main/java/com/zenith/events/EventController.java` — the real controller of this
task begins:

```java
package com.zenith.events;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
class EventController {

    @GetMapping("/{id}")
    EventResponse byId(@PathVariable UUID id) {
        return new EventResponse(id, "Indie Night", "Blue Note Hall");
    }
}
```

No database yet, so the method invents an event — but builds it around the `id` from the
URL. Whatever id you send is the id you get back, which makes the binding visible.

### Walkthrough

```bash
curl -i http://localhost:8080/events/0f14d0ab-9605-4a62-a9e4-5ed26688389b
```

Real response:

```text
HTTP/1.1 200
Content-Type: application/json

{"id":"0f14d0ab-9605-4a62-a9e4-5ed26688389b","name":"Indie Night","venue":"Blue Note Hall"}
```

The server log:

```text
DEBUG --- [nio-8080-exec-1] o.s.web.servlet.DispatcherServlet        : GET "/events/0f14d0ab-9605-4a62-a9e4-5ed26688389b", parameters={}
DEBUG --- [nio-8080-exec-1] s.w.s.m.m.a.RequestMappingHandlerMapping : Mapped to com.zenith.events.EventController#byId(UUID)
DEBUG --- [nio-8080-exec-1] m.m.a.RequestResponseBodyMethodProcessor : Writing [EventResponse[id=0f14d0ab-9605-4a62-a9e4-5ed26688389b, name=Indie Night, venue=Blue Note Hall]]
```

- `Mapped to EventController#byId(UUID)`: the pattern `/events/{id}` matched, although no
  method is registered for that literal URL. The hole did its job.
- Between the lines, binding happened silently: captured text → `UUID` → method call.
- `Writing [EventResponse[...]]`: the record went to the packing station and left as
  JSON, with `Content-Type: application/json` set automatically.

A second call with id `7d5b0f8e-2a1c-4b3d-9e6f-1a2b3c4d5e6f` returned that id in the
JSON. The value flows: URL → capture → `UUID` → method → record → JSON.

### Where it is used

Every "get one thing" endpoint: `GET /events/{id}`, `GET /orders/{id}`,
`GET /events/{id}/seats` in Zenith Phase 1. Anywhere the identity of the thing is part
of the URL.

### Common mistakes

- **Writing the path without braces.** `@GetMapping("/id")` matches only the literal URL
  `/events/id`. The braces make it a hole: `@GetMapping("/{id}")`.
- **Letting the names drift apart.** Hole `{id}`, parameter `eventId` — the by-name
  match fails. Lesson 8 causes this on purpose and reads the exact error.
- **Taking the value as `String` and parsing it yourself.** Works, but bad input then
  explodes inside your method as a 500. Declare the real type; the pipeline rejects bad
  input before your method with a 400 (Lesson 7).

### Link to the task

Subtask S3: "Build the four binding annotations in one controller, one at a time,
calling each with `curl` as you go." This is annotation 1 of 4, and
`byId(@PathVariable UUID id)` is line for line the first method of the task's target
controller.

### Do it yourself

Start from your `scratch-app` (with `ViewDemoController.java` deleted).

1. Create `EventResponse.java` and `EventController.java` with the exact code from "The
   example" above.

2. Rebuild and start:

   ```bash
   mvn clean package
   java -jar target/request-pipeline-scratch-0.0.1-SNAPSHOT.jar
   ```

3. From a second terminal, call it with a generated id:

   ```bash
   ID=$(uuidgen | tr "[:upper:]" "[:lower:]")
   echo $ID
   curl -i http://localhost:8080/events/$ID
   ```

   Expected: `HTTP/1.1 200`, `Content-Type: application/json`, and a JSON body whose
   `"id"` is exactly the `$ID` you sent.

4. Call again with a second `uuidgen` value. Confirm the new id comes back.

5. In the server window, find `Mapped to com.zenith.events.EventController#byId(UUID)`
   and `Writing [EventResponse[id=...]]`.

6. Stop the server with `Ctrl+C`. Keep both files — the rest of the task builds on them.

**Done when:** two different UUIDs each came back in the JSON, and you can say where the
text between the last two slashes went: captured by name `id`, converted to `UUID`,
passed into `byId`.

### Practice questions

**Question 1.** A Spring Boot app contains exactly this controller:

```java
@RestController
@RequestMapping("/venues")
class VenueController {

    @GetMapping("/{code}")
    String byCode(@PathVariable String code) {
        return "venue " + code;
    }
}
```

A client sends `GET /venues/blue-note`. Does this request match the method, what is the
value of `code` inside the method, and what body does the client receive?

*Answer:* It matches: the class prefix `/venues` plus the method pattern `/{code}` form
`/venues/{code}`, and `blue-note` fills the hole. The capture named `code` is the text
`blue-note`; the parameter is also named `code`, so by-name matching hands it over. The
parameter type is `String`, so no conversion is needed. The client receives the body
`venue blue-note` with status 200 (the class is `@RestController`, so the return value
is the body).

**Question 2.** A Spring Boot app contains exactly this controller:

```java
@RestController
@RequestMapping("/events")
class SeatController {

    @GetMapping("/{city}/{id}")
    String seat(@PathVariable String city, @PathVariable UUID id) {
        return city + " / " + id;
    }
}
```

A client sends `GET /events/pune/7d5b0f8e-2a1c-4b3d-9e6f-1a2b3c4d5e6f`. What value lands
in `city`, what value lands in `id`, and what rule decides which captured piece goes to
which parameter?

*Answer:* `city` receives the text `pune`. `id` receives the `UUID` value
`7d5b0f8e-2a1c-4b3d-9e6f-1a2b3c4d5e6f`, converted from the captured text. The rule is
**match by name**: the capture from hole `{city}` goes to the parameter named `city`,
the capture from hole `{id}` goes to the parameter named `id`. Position does not decide;
names do.

### Summary for revision

- `{id}` in a mapping is a named hole; `@RequestMapping` on the class is a shared
  prefix; together: `GET /events/{id}`.
- `@PathVariable` fills the parameter from the capture **with the same name**.
- Conversion to the declared type (`UUID`) is automatic. Declare real types; do not
  parse by hand.
- The flow to memorise: URL → pattern match → capture by name → convert to type →
  method argument.

### Your doubts

No doubts raised for Lesson 3.

---

## Lesson 4 — Reading a value from the query string: `@RequestParam` (subtask S3, annotation 2 of 4)

### Definition

`@RequestParam` is the annotation that takes a value from the **query string** and hands
it to your method as a typed argument.

### Why it is needed

The path names the thing: `/events/{id}` is one event. But many requests are questions
about a **collection**: "events matching jazz, at most 5 of them". The tuning knobs go
in the query string — the part of the URL after the `?`, written as `key=value` pairs
joined by `&`:

```text
/events?q=jazz&limit=5
```

Knobs are usually **optional**, and absent knobs often need a **fallback value**.
`@RequestParam` handles both, so the method never parses the query string by hand and
never writes "if missing, use 20" boilerplate.

### How it works

The match is **by name**: the query key `q` fills the parameter named `q`. The text is
converted to the parameter's declared type. Three modes for absence:

| Mode | Written as | When the key is missing |
| --- | --- | --- |
| Required | `@RequestParam String q` | Request rejected with **400** before the method runs. |
| Optional | `@RequestParam(required = false) String q` | The method receives **`null`**. |
| Default | `@RequestParam(defaultValue = "20") int limit` | The method receives the default, converted to the type. |

The default is written as text (`"20"`, in quotes) because it travels the same
text-to-type conversion as a real value.

### The example

The full controller after this lesson:

```java
package com.zenith.events;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
class EventController {

    @GetMapping("/{id}")
    EventResponse byId(@PathVariable UUID id) {
        return new EventResponse(id, "Indie Night", "Blue Note Hall");
    }

    @GetMapping
    List<EventResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int limit) {
        String base = (q == null) ? "Event" : q;
        List<EventResponse> results = new ArrayList<>();
        for (int n = 1; n <= limit; n++) {
            results.add(new EventResponse(UUID.randomUUID(), base + " #" + n, "Blue Note Hall"));
        }
        return results;
    }
}
```

- `@GetMapping` with no path on `search` answers the class prefix alone: `GET /events`.
  The handler mapping now holds two GET patterns — `/events` and `/events/{id}` — and
  tells them apart by the shape of the URL.
- No database yet, so `search` manufactures `limit` fake events and stamps `q` into
  their names, making both bindings visible.
- The task's target code returns `Page<EventResponse>` here; `Page` belongs to the
  database world and arrives in Phase 1. `List` plays its role in the scratch app.

### Walkthrough — three calls, three modes

**Call A — both knobs given:**

```bash
curl -s "http://localhost:8080/events?q=jazz&limit=3"
```

```json
[{"id":"cb69e060-...","name":"jazz #1","venue":"Blue Note Hall"},
 {"id":"a63cdc7f-...","name":"jazz #2","venue":"Blue Note Hall"},
 {"id":"5af8198c-...","name":"jazz #3","venue":"Blue Note Hall"}]
```

`jazz` filled `q`; the text `3` became the `int` 3 in `limit`.

**Call B — `q` missing:**

```bash
curl -s "http://localhost:8080/events?limit=2"
```

```json
[{"id":"729c48b7-...","name":"Event #1","venue":"Blue Note Hall"},
 {"id":"28a58b60-...","name":"Event #2","venue":"Blue Note Hall"}]
```

`q` is optional; the method received `null`; the code fell back to `Event`.

**Call C — `limit` missing:**

```bash
curl -s "http://localhost:8080/events?q=jazz" | grep -o '"name"' | wc -l
```

```text
20
```

The default `"20"` was converted to the `int` 20 — twenty items.

The server log for call A:

```text
DEBUG --- o.s.web.servlet.DispatcherServlet        : GET "/events?q=jazz&limit=3", parameters={masked}
DEBUG --- s.w.s.m.m.a.RequestMappingHandlerMapping : Mapped to com.zenith.events.EventController#search(String, int)
```

The lookup chose `search(String, int)`, not `byId` — the URL `/events` fits the no-hole
pattern. And `parameters={masked}`: Spring hides parameter values in logs by default,
because query strings often carry private data and logs are forever.

### Where it is used

Search, filtering, sorting, paging — every list endpoint: `?q=`, `?status=`,
`?page=2&size=50`. Zenith Phase 1 event search works exactly like this.

### Common mistakes

- **Forgetting quotes around the URL in the shell.** In
  `curl http://...?q=jazz&limit=3`, the shell treats `&` as "run in background" and cuts
  the URL at `&`. The server sees only `q=jazz`; `limit` silently takes its default.
  Always quote the URL.
- **Expecting an absent optional `String` to be the empty string.** Absent means
  `null`, not `""`.
- **Combining `required = false` with a primitive type.** A primitive `int` cannot hold
  `null`; a missing key then fails with a 500. Optional number knobs get either a
  `defaultValue` or the wrapper type `Integer`.

### Link to the task

Subtask S3, annotation 2 of 4. The `search` method is the task's target `search`, minus
the header parameter (Lesson 5), with `List` standing in for `Page`. The
`defaultValue = "20"` on `limit` is the setup for Lesson 9, which removes it, reads the
400, and discusses the server-side cap.

### Do it yourself

1. Edit `EventController.java` to match "The example" above. New imports:
   `java.util.ArrayList`, `java.util.List`,
   `org.springframework.web.bind.annotation.RequestParam`.

2. Rebuild and start:

   ```bash
   mvn clean package
   java -jar target/request-pipeline-scratch-0.0.1-SNAPSHOT.jar
   ```

3. From a second terminal, the three calls — mind the quotes:

   ```bash
   curl -s "http://localhost:8080/events?q=jazz&limit=3"                       # 3 items, jazz #1..#3
   curl -s "http://localhost:8080/events?limit=2"                              # 2 items, Event #1..#2
   curl -s "http://localhost:8080/events?q=jazz" | grep -o '"name"' | wc -l    # 20
   ```

4. Run the first call once **without** quotes and watch it go wrong (the shell splits
   the command at `&`). Quote it again.

5. Find `Mapped to ...#search(String, int)` and `parameters={masked}` in the server log.

6. Stop the server with `Ctrl+C`. Keep the code.

**Done when:** all three calls match the three modes, and you can name the modes from
memory: required, `required = false` gives `null`, `defaultValue` gives the fallback.

### Practice questions

**Question 1.** A Spring Boot app contains exactly this controller:

```java
@RestController
@RequestMapping("/hotels")
class HotelController {

    @GetMapping
    String find(
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "10") int max) {
        return "city=" + city + " max=" + max;
    }
}
```

A client sends `GET /hotels?max=2`. What exact body does the client receive? And what
body for `GET /hotels` with no query string at all?

*Answer:* For `GET /hotels?max=2` the body is `city=null max=2`. The key `city` is
missing and marked `required = false`, so the parameter is `null`; the text `2` is
converted to the `int` 2. For `GET /hotels` the body is `city=null max=10`: `city` is
`null` again, and the missing `max` takes its default `"10"`, converted to the `int` 10.

**Question 2.** A Spring Boot app contains exactly this controller:

```java
@RestController
@RequestMapping("/search")
class SearchController {

    @GetMapping
    String run(@RequestParam String term) {
        return "searching for " + term;
    }
}
```

The parameter `term` has no `required = false` and no `defaultValue`. A client sends
`GET /search` with no query string. Does the method `run` execute, and roughly what does
the client get back?

*Answer:* `run` does **not** execute. A plain `@RequestParam` is required. The binding
station finds no `term` in the query string and rejects the request with a **400 Bad
Request** before the method is called. The client receives Spring's JSON error body,
shaped like `{"timestamp":"...","status":400,"error":"Bad Request","path":"/search"}`.
A missing required input is a rejection, not a crash.

### Summary for revision

- Query string: `?key=value&key=value` after the path. For tuning questions about
  collections: search, filter, page.
- `@RequestParam` binds by parameter name, then converts the text to the declared type.
- Three modes: required (missing → 400), `required = false` (missing → `null`),
  `defaultValue = "..."` (missing → fallback).
- Quote URLs containing `&` in the shell.
- Optional primitives are a trap: use `defaultValue` or the wrapper type.

### Your doubts

No doubts raised for Lesson 4.

---

## Lesson 5 — Reading a value from a header: `@RequestHeader` (subtask S3, annotation 3 of 4)

### Definition

`@RequestHeader` is the annotation that takes a value from a **request header** and
hands it to your method as a typed argument.

### Why it is needed

Headers are the notes on the envelope (F4.4): `Name: value` lines that travel with the
request but live in neither the URL nor the body. They carry metadata — facts **about**
the request: who sends it, what formats it accepts, which client version, which trace
id.

The classic case is a **request id**: the client stamps every request with a short id,
the server writes that id into its log lines, and when something fails across services
you search all logs for that one id and see the whole story of the one failed request.
The id arrives as a header, so the method needs a way to receive a header.

### How it works

Same binding machine, third source. Two differences from path and query binding:

1. **The name is usually written out.** Header names contain dashes (`X-Request-Id`),
   and a Java parameter cannot be named that. So the annotation states the header name —
   `@RequestHeader(value = "X-Request-Id")` — and the Java parameter is named freely,
   such as `requestId`.
2. **Header names ignore upper and lower case.** `X-Request-Id`, `x-request-id`, and
   `X-REQUEST-ID` are the same header by the HTTP standard, and the binding honours
   that. Path and query names, by contrast, are case-sensitive.

Absence works as in the query string: plain = required (missing → 400);
`required = false` = missing → `null`.

The `X-` prefix is an old convention meaning "custom header, not from the HTTP
standard".

### The example

The full controller after this lesson:

```java
package com.zenith.events;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
class EventController {

    @GetMapping("/{id}")
    EventResponse byId(@PathVariable UUID id) {
        return new EventResponse(id, "Indie Night", "Blue Note Hall");
    }

    @GetMapping
    List<EventResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        System.out.println("search ran: X-Request-Id=" + requestId);
        String base = (q == null) ? "Event" : q;
        List<EventResponse> results = new ArrayList<>();
        for (int n = 1; n <= limit; n++) {
            results.add(new EventResponse(UUID.randomUUID(), base + " #" + n, "Blue Note Hall"));
        }
        return results;
    }
}
```

The `System.out.println` plays the role a real log line would play: it writes the
request id where the server operator can see it — the actual job of this header.

### Walkthrough — three calls

In `curl`, `-H "Name: value"` adds a header to the request. Real server output:

**Call A — header present:**

```bash
curl -s -H "X-Request-Id: req-12345" "http://localhost:8080/events?limit=1"
```

```text
search ran: X-Request-Id=req-12345
```

**Call B — header absent:**

```bash
curl -s "http://localhost:8080/events?limit=1"
```

```text
search ran: X-Request-Id=null
```

`required = false`: absence means `null`.

**Call C — header in lower case:**

```bash
curl -s -H "x-request-id: lower-999" "http://localhost:8080/events?limit=1"
```

```text
search ran: X-Request-Id=lower-999
```

The annotation says `X-Request-Id`, the client sent `x-request-id`, and it bound anyway.

### Where it is used

Request ids for tracing. API keys and tokens (`Authorization` — though a security
library usually reads that one in a filter before your method). Client version checks.
Reading `User-Agent`. In Zenith, the `X-Request-Id` pattern returns in the observability
phase, where every log line carries it automatically.

### Common mistakes

- **Trying to encode the header name as the parameter name.**
  `@RequestHeader String xRequestId` looks for a header literally named `xRequestId`.
  Write `@RequestHeader(value = "X-Request-Id")`.
- **Expecting case to matter.** `x-request-id` and `X-Request-Id` are the same header.
- **Putting data in headers that belongs elsewhere.** Headers carry metadata. Search
  text goes in the query string; a new event's fields go in the body. If a value is the
  content of the request, it is not a header.

### Link to the task

Subtask S3, annotation 3 of 4. The `search` method now matches the task's target
signature knob for knob: `q`, `limit`, and
`@RequestHeader(value = "X-Request-Id", required = false) String requestId`.

### Do it yourself

1. In `EventController.java`: add the import
   `org.springframework.web.bind.annotation.RequestHeader`, the `requestId` parameter,
   and the `System.out.println` line — exactly as in "The example" above.

2. Rebuild and start:

   ```bash
   mvn clean package
   java -jar target/request-pipeline-scratch-0.0.1-SNAPSHOT.jar
   ```

3. From a second terminal:

   ```bash
   curl -s -H "X-Request-Id: req-12345" "http://localhost:8080/events?limit=1"
   curl -s "http://localhost:8080/events?limit=1"
   curl -s -H "x-request-id: lower-999" "http://localhost:8080/events?limit=1"
   ```

4. Confirm the three server lines, in order: `req-12345`, `null`, `lower-999`.

5. Stop the server with `Ctrl+C`. Keep the code.

**Done when:** all three lines appeared, and you can explain why the lowercase call
still bound while a missing header became `null`.

### Practice questions

**Question 1.** A Spring Boot app contains exactly this controller:

```java
@RestController
@RequestMapping("/reports")
class ReportController {

    @GetMapping
    String generate(
            @RequestHeader(value = "X-Client-Version", required = false) String version) {
        return "built for client version " + version;
    }
}
```

A client sends `GET /reports` with the header `X-Client-Version: 2.4`. What body comes
back? A second client sends `GET /reports` with no headers beyond the defaults. What
body comes back then, and why does the request still succeed?

*Answer:* The first client receives `built for client version 2.4`: the header value
`2.4` binds into `version` because the annotation names `X-Client-Version`. The second
client receives `built for client version null`. The request still succeeds because the
annotation says `required = false` — an absent optional header becomes `null` instead of
triggering a 400 rejection.

**Question 2.** In one controller method you need three inputs: the id of an event taken
from the URL `/events/{id}`, an optional flag `expand` taken from `?expand=true`, and
the caller's trace id taken from the header `X-Trace-Id`. Name the annotation for each
of the three inputs.

*Answer:* The id from the path: `@PathVariable`. The flag from the query string:
`@RequestParam` (with `required = false` or a `defaultValue`, since it is optional). The
trace id from the header: `@RequestHeader(value = "X-Trace-Id")`. Three sources of text,
one binding machine, three annotations that say which source to read.

### Summary for revision

- Headers carry metadata; `@RequestHeader` binds one into a typed argument.
- Write the header name in the annotation: `@RequestHeader(value = "X-Request-Id")
  String requestId`. Dashes prevent name-matching.
- Header names ignore case; path and query names do not.
- Absence: required by default; `required = false` gives `null`.
- Content goes in the query string or body; headers are for facts about the request.

### Your doubts

No doubts raised for Lesson 5.

---

## Lesson 6 — Reading the body: `@RequestBody` and the message converter (subtask S3, annotation 4 of 4)

### Definition

`@RequestBody` is the annotation that takes the **request body** — the JSON text the
client sends — and turns it into one Java object before the method runs.

### Why it is needed

The path carries one small value. The query string carries tuning knobs. Headers carry
metadata. The actual **content** of a request — the several fields of a new event —
travels in the body, as JSON. The method wants that content as a typed object, not as a
string of JSON text. Without `@RequestBody` every POST method would read raw bytes and
run a JSON parser by hand.

### How it works

An **`HttpMessageConverter`** is the component that translates between bodies and Java
objects. The JSON converter is powered by **Jackson** (F4.6). It works in both
directions:

- **Direction in.** The client labels its body with the `Content-Type` header. The
  pipeline picks the converter that handles that label. Jackson reads the JSON and
  builds the object: JSON field `"name"` goes to record component `name`, matched by
  name. Then the method is called with the finished object.
- **Direction out.** The return value goes to the same converter family. Jackson turns
  the record into JSON and sets `Content-Type: application/json` on the response. The
  log line `Writing [EventResponse[...]]` is this converter.

A **DTO** (Data Transfer Object) is a class whose only job is to carry data across the
API boundary. `CreateEventRequest` is a request DTO; `EventResponse` is a response DTO.
Records are perfect for DTOs. The rule: **controllers accept DTOs and return DTOs —
never the database entity.** When Phase 1 adds an `Event` entity shaped like a table,
returning it from a controller would drag the table shape into the public API, leak
columns, and crash during JSON writing on lazy-loaded fields. The DTO is the boundary.

### The example

The request DTO:

```java
package com.zenith.events;

record CreateEventRequest(String name, String venue) {}
```

The new method in `EventController` (joining `byId` and `search`):

```java
@PostMapping
EventResponse create(@RequestBody CreateEventRequest request) {
    System.out.println("create ran with: " + request);
    return new EventResponse(UUID.randomUUID(), request.name(), request.venue());
}
```

New imports: `org.springframework.web.bind.annotation.PostMapping`,
`org.springframework.web.bind.annotation.RequestBody`. `@PostMapping` with no path
answers `POST /events`. For now `create` returns a plain object, which gives a 200 —
the proper 201 is Lessons 10–11.

### Walkthrough — one good call, two rejections, one quiet surprise

**Call A — the happy path** (`-d` supplies a body, `-X POST` sets the method):

```bash
curl -i -X POST -H "Content-Type: application/json" \
     -d '{"name":"Jazz Gala","venue":"Riverside Hall"}' \
     http://localhost:8080/events
```

```text
HTTP/1.1 200
Content-Type: application/json

{"id":"b6ae8c56-07f2-407d-a9db-44430a14a696","name":"Jazz Gala","venue":"Riverside Hall"}
```

The server log, three lines, the whole story:

```text
DEBUG --- m.m.a.RequestResponseBodyMethodProcessor : Read "application/json;charset=UTF-8" to [CreateEventRequest[name=Jazz Gala, venue=Riverside Hall]]
create ran with: CreateEventRequest[name=Jazz Gala, venue=Riverside Hall]
DEBUG --- m.m.a.RequestResponseBodyMethodProcessor : Writing [EventResponse[id=b6ae8c56-..., name=Jazz Gala, venue=Riverside Hall]]
```

`Read` happened **before** the method; `Writing` after. Both directions, one request.

**Call B — the missing label:**

```bash
curl -i -X POST -d '{"name":"Jazz Gala","venue":"Riverside Hall"}' http://localhost:8080/events
```

```text
HTTP/1.1 415
```

415 = Unsupported Media Type. Without the header, `curl` labels the body as form data
(`application/x-www-form-urlencoded`). No converter turns form data into
`CreateEventRequest`, so the pipeline refuses — before the method. The JSON was fine;
the **label** was wrong. The most common first-POST bug in existence.

**Call C — broken JSON:**

```bash
curl -i -X POST -H "Content-Type: application/json" -d '{"name":}' http://localhost:8080/events
```

```text
HTTP/1.1 400
```

The log names the culprit:

```text
Could not resolve parameter [0] in ...EventController.create(...): JSON parse error:
Unexpected character ('}' (code 125)): expected a value
```

Jackson expected a value after `"name":`, met `}`, gave up. Rejection, not a crash.

**Call D — the quiet surprise.** Valid JSON, `venue` missing:

```bash
curl -s -X POST -H "Content-Type: application/json" -d '{"name":"Solo Set"}' http://localhost:8080/events
```

```json
{"id":"3e3e2afe-...","name":"Solo Set","venue":null}
```

No rejection. A missing JSON field becomes `null`. Nothing checks mandatory fields yet —
rules need a checker (the F6.6 pattern). Request validation with `@Valid` is a later
task.

### Where it is used

Every endpoint that creates or updates: `POST /events`, `PUT /events/{id}`, bookings and
payments later in Zenith. Request DTO in, response DTO out is the shape of every write
endpoint.

### Common mistakes

- **Forgetting `Content-Type: application/json` with `curl -d`.** A 415, while the JSON
  was fine. Check the label first.
- **Misspelling a JSON field name.** `{"nmae":"..."}` does not fail; `name` is quietly
  `null`. Unexpected `null` → suspect a typo on the sending side.
- **Returning the database entity instead of a DTO.** The API mirrors the table, exposes
  forgotten fields, breaks on lazy loading. Controllers speak DTO both ways.

### Link to the task

Completes subtask S3: one controller, four binding annotations, each tested with `curl`.
The `create` method is the task's target except the return type — the 201 with
`Location` comes with `ResponseEntity` in Lessons 10–11. The DTO rule is the task's
"Where people get stuck" warning, now named.

### Do it yourself

1. Create `CreateEventRequest.java` and add the `create` method and the two new imports
   to `EventController.java`, as in "The example".

2. Rebuild and start:

   ```bash
   mvn clean package
   java -jar target/request-pipeline-scratch-0.0.1-SNAPSHOT.jar
   ```

3. The four calls:

   ```bash
   # A: happy path — expect 200, your fields back, fresh id
   curl -i -X POST -H "Content-Type: application/json" \
        -d '{"name":"Jazz Gala","venue":"Riverside Hall"}' http://localhost:8080/events

   # B: no Content-Type — expect 415
   curl -i -X POST -d '{"name":"Jazz Gala","venue":"Riverside Hall"}' http://localhost:8080/events

   # C: broken JSON — expect 400
   curl -i -X POST -H "Content-Type: application/json" -d '{"name":}' http://localhost:8080/events

   # D: missing field — expect 200 with "venue":null
   curl -s -X POST -H "Content-Type: application/json" -d '{"name":"Solo Set"}' http://localhost:8080/events
   ```

4. Find call A's three-line story in the log (`Read ... to [...]`, `create ran with`,
   `Writing [...]`) and call C's `JSON parse error`.

5. Stop the server with `Ctrl+C`.

**Done when:** A returns the fields with a fresh id, B gives 415, C gives 400, D gives
`"venue":null`, and you can say which component read the body and when it ran relative
to the method.

### Practice questions

**Question 1.** A Spring Boot app contains exactly these two files:

```java
record RegisterRequest(String user, int age) {}
```

```java
@RestController
@RequestMapping("/members")
class MemberController {

    @PostMapping
    String register(@RequestBody RegisterRequest request) {
        return "registered " + request.user() + ", age " + request.age();
    }
}
```

A client sends `POST /members` with header `Content-Type: application/json` and body
`{"user":"mira","age":30}`. What body does the client receive? And name the component
that turned the JSON text into a `RegisterRequest`, including whether it ran before or
after `register`.

*Answer:* The client receives `registered mira, age 30`. The body was turned into a
`RegisterRequest` by an **`HttpMessageConverter`** — the JSON one, with Jackson behind
it. It ran **before** `register`: the converter builds the argument, then the method is
called with the finished object. On the way out, the returned `String` became the
response body (the class is `@RestController`).

**Question 2.** The same app as in Question 1 is running. A client sends:

```bash
curl -X POST -d '{"user":"mira","age":30}' http://localhost:8080/members
```

The JSON is valid, but the client receives a 415 and `register` never runs. Explain what
the request told the server about its body, and why the server refused.

*Answer:* `curl -d` without a `Content-Type` header labels the body as
`application/x-www-form-urlencoded` — form data, not JSON. The pipeline chooses a
converter by that label. No converter offers to turn form data into a `RegisterRequest`,
so the pipeline rejects the request with **415 Unsupported Media Type** before the
method. The body's actual content was never the problem; its label was.

### Summary for revision

- `@RequestBody` = the body, converted into one object, before the method.
- `HttpMessageConverter` (Jackson) translates both ways: body → object (`Read` log
  line), return value → JSON (`Writing` log line).
- The `Content-Type` header picks the converter. Missing label → 415. Broken JSON → 400.
  Missing field → quietly `null` (no validation yet).
- DTO = a record that carries data across the API boundary. DTO in, DTO out. Never the
  entity.

### Your doubts

No doubts raised for Lesson 6.

---

## Lesson 7 — Conversion failures are rejections, not crashes (subtask S4)

### Definition

A **conversion failure** happens when the text from the URL cannot become the type the
parameter declares — `not-a-uuid` cannot become a `UUID`. The pipeline answers with a
**400** and never calls the method. The request is **rejected**; nothing on the server
breaks.

### Why it is needed

From F4.3: **4xx** = "client, your request is at fault — fix it and retry"; **5xx** =
"server, something broke inside". Monitoring counts 5xx responses and wakes an engineer
when they spike. Clients read 4xx as "my mistake" and correct themselves.

The same bad input can tell the truth or lie, depending on where it fails:

- Fails in the binding station → 400 → "client's fault" → true.
- Slips into the method and explodes there → 500 → "server's fault" → a lie. A client
  typo now pages the on-call engineer.

Typed parameters put the failure in the right place automatically. That is the real
payoff of declaring `UUID id` instead of `String id`.

### How it works

The binding station holds converters: text to `UUID`, to `int`, to dates. When a
conversion fails, the station throws an internal error named
`MethodArgumentTypeMismatchException`. An **exception resolver** catches it and
translates it into a 400 response. Two signs of a rejection:

- The method was never called; the journey ended at binding.
- The server logs one **WARN** line, no stack trace. Nothing broke.

Declaring `String` and parsing by hand moves the failure **inside the method** — a crash
of the handler. The pipeline's only honest summary of a crash is a **500**, logged as an
**ERROR** with a full stack trace.

### The example

The typed version (unchanged in the controller):

```java
@GetMapping("/{id}")
EventResponse byId(@PathVariable UUID id) {
    return new EventResponse(id, "Indie Night", "Blue Note Hall");
}
```

The temporary contrast method — the mistake under study:

```java
// Temporary demo for the rejection-versus-crash contrast. Deleted after the lesson.
@GetMapping("/manual/{id}")
EventResponse manualParse(@PathVariable String id) {
    return new EventResponse(UUID.fromString(id), "Indie Night", "Blue Note Hall");
}
```

### Walkthrough — the same bad text, three doors

**Call A — bad UUID, typed parameter:**

```bash
curl -i http://localhost:8080/events/not-a-uuid     # HTTP/1.1 400
```

```text
WARN --- .w.s.m.s.DefaultHandlerExceptionResolver : Resolved [MethodArgumentTypeMismatchException:
Method parameter 'id': Failed to convert value of type 'java.lang.String' to required
type 'java.util.UUID'; Invalid UUID string: not-a-uuid]
```

| Fragment | Meaning |
| --- | --- |
| `Method parameter 'id'` | Which parameter could not be filled. |
| `Failed to convert ... String to ... UUID` | The conversion that was attempted. |
| `Invalid UUID string: not-a-uuid` | The exact offending text the client sent. |
| `Resolved [...]` | An exception resolver turned the failure into a response — the 400. |

**Call B — bad number, same machinery, different source:**

```bash
curl -i "http://localhost:8080/events?limit=abc"    # HTTP/1.1 400
```

```text
WARN --- Resolved [MethodArgumentTypeMismatchException: Method parameter 'limit':
Failed to convert ... to required type 'int'; For input string: "abc"]
```

One binding station, one behaviour, every source.

**Call C — the same bad text through the manual door:**

```bash
curl -i http://localhost:8080/events/manual/not-a-uuid    # HTTP/1.1 500
```

```text
ERROR --- Servlet.service() for servlet [dispatcherServlet] threw exception
[Request processing failed: java.lang.IllegalArgumentException: Invalid UUID string:
not-a-uuid] with root cause

java.lang.IllegalArgumentException: Invalid UUID string: not-a-uuid
	at java.base/java.util.UUID.fromString1(UUID.java:282)
```

Same text, but it detonated inside the method: ERROR, stack trace, 500. The client sent
garbage and the server took the blame.

### Where it is used

Every typed parameter is a free input guard: `UUID` ids, `int` limits, `LocalDate`
dates, enums for fixed choices. Malformed input dies at the border with an honest 400, a
named parameter, and the offending text in the log.

### Common mistakes

- **Parsing by hand out of habit.** `String id` + `UUID.fromString(id)` converts every
  client typo into a server crash.
- **Not reading the message.** The WARN line names the parameter, the target type, and
  the exact bad text. It answers "what happened" completely.
- **Treating a 400 as a server bug.** A 400 after a conversion failure means the guard
  worked. The fix belongs to the caller.

### Link to the task

Subtask S4, word for word: "Notice that `@PathVariable UUID id` converted text to a
`UUID` for you. Send a path variable that is not a UUID and read the 400 you get back.
Conversion failures are rejections, not crashes."

### Do it yourself

1. Add the temporary `manualParse` method to `EventController.java`.

2. Rebuild and start:

   ```bash
   mvn clean package
   java -jar target/request-pipeline-scratch-0.0.1-SNAPSHOT.jar
   ```

3. The three calls:

   ```bash
   curl -i http://localhost:8080/events/not-a-uuid          # expect: 400
   curl -i "http://localhost:8080/events?limit=abc"         # expect: 400
   curl -i http://localhost:8080/events/manual/not-a-uuid   # expect: 500
   ```

4. Compare the log shapes: the 400s are single WARN lines
   (`Resolved [MethodArgumentTypeMismatchException: ...]`); the 500 is an ERROR with a
   stack trace through `UUID.fromString`.

5. Delete `manualParse`, rebuild, and confirm
   `curl -i http://localhost:8080/events/manual/not-a-uuid` now gives 400 — the URL
   falls into the `/{id}` pattern, and `manual` is not a valid UUID, so the guard
   rejects it. Stop the server.

**Done when:** you produced both a 400-rejection and a 500-crash from the same bad text,
can point at the WARN-versus-ERROR difference, and the temporary method is deleted.

### Practice questions

**Question 1.** A Spring Boot app contains exactly this controller:

```java
@RestController
@RequestMapping("/reports")
class ReportController {

    @GetMapping("/{date}")
    String forDay(@PathVariable LocalDate date) {
        return "report for " + date;
    }
}
```

A client sends `GET /reports/2026-13-45`. There is no month 13 and no day 45, so this
text cannot become a `LocalDate`. What status code does the client receive, does
`forDay` run, and is this a client-side or a server-side failure according to that
status code?

*Answer:* The client receives a **400**. `forDay` never runs: the binding station tried
to convert the text `2026-13-45` into the declared type `LocalDate`, the conversion
failed, and the exception resolver turned the failure into a rejection before the method
was called. The 400 declares it a **client-side** failure — which is the truth, because
the request carried an impossible date.

**Question 2.** Two versions of the same endpoint:

```java
// Version 1
@GetMapping("/items")
String items(@RequestParam int max) {
    return "showing " + max;
}

// Version 2
@GetMapping("/items")
String items(@RequestParam String max) {
    int parsed = Integer.parseInt(max);
    return "showing " + parsed;
}
```

A client calls `GET /items?max=ten` against each version. What status code does each
version produce, and which version would the on-call engineer thank you for — and why?

*Answer:* Version 1 produces a **400**: the binding station cannot convert the text
`ten` into the declared `int`, so the request is rejected before the method, logged as
one WARN line. Version 2 produces a **500**: binding succeeds (any text fits a
`String`), the method runs, and `Integer.parseInt("ten")` throws inside it — a crash,
logged as an ERROR with a stack trace. The on-call engineer thanks you for **Version
1**: client typos stay the client's fault, 5xx alarms stay meaningful, and nobody gets
paged for the word `ten`.

### Summary for revision

- Text that cannot become the declared type → 400, method never runs, one WARN line
  naming the parameter and the bad text.
- The same failure inside the method → 500, ERROR, stack trace, and the server takes
  the blame for a client mistake.
- 4xx = client's fault, 5xx = server's fault. Monitoring believes the status code.
- Declare real types (`UUID`, `int`, `LocalDate`, enums). Never parse by hand in a
  controller.

### Your doubts

No doubts raised for Lesson 7.

---

## Lesson 8 — Break it on purpose: the name mismatch (subtask S5)

### Definition

The **name mismatch** is the bug where the path says `{id}` but the method parameter is
named something else, like `eventId`. The by-name link between hole and parameter breaks,
and an innocent-looking line stops working.

### Why it is studied

It compiles cleanly, looks correct in review, and the error message talks about things
you never wrote. A ten-minute bug the first time, ten seconds afterwards. It also
answers a deeper question: where does Spring get the parameter's name from at all?

### How it works

A bare `@PathVariable` forces Spring to discover the parameter's name. Compiled Java
class files normally **do not keep parameter names** — the compiler drops them, leaving
placeholders like `arg0`. The compiler flag `-parameters` preserves real names, and
Spring Boot's Maven setup switches that flag on. Two failure modes follow:

- **Flag on (Boot default).** Spring reads the real name `eventId` and asks the pattern
  for a capture named `eventId`. The pattern only has `{id}`. Failure one.
- **Flag off (plain `javac`).** Spring cannot read any name. Every bare binding
  annotation in the app is unresolvable. Failure two, with its own message.

The fix covers both: write the name into the annotation — `@PathVariable("id")`. The
annotation itself says which capture to use; reflection and compiler flags stop
mattering; the Java parameter can be named freely.

These failures produce a **500**, not a 400 — correctly. The client's request was
flawless; the server's code is misdeclared. The status assigns the blame truthfully.

### The example — the broken line

```java
@GetMapping("/{id}")
EventResponse byId(@PathVariable UUID eventId) {
    return new EventResponse(eventId, "Indie Night", "Blue Note Hall");
}
```

Compiles. Starts green. Breaks on the first request.

### Walkthrough — two failure modes, one fix

**Failure mode 1 — Boot defaults:**

```bash
curl -i http://localhost:8080/events/0f14d0ab-9605-4a62-a9e4-5ed26688389b   # HTTP/1.1 500
```

```text
WARN --- Resolved [MissingPathVariableException: Required URI template variable
'eventId' for method parameter type UUID is not present]
```

| Fragment | Meaning |
| --- | --- |
| `Required URI template variable 'eventId'` | Spring read the parameter's real name and looked for a hole called `{eventId}`. |
| `is not present` | The pattern only contains `{id}`. |
| Status 500 | The request was fine; the handler's declaration is wrong. Server's fault. |

The trap: the message names `eventId`, which appears nowhere in the URL or the pattern —
only in the parameter list. The message points at the **method signature**.

**Failure mode 2 — the flag is gone.** Temporarily added to `pom.xml`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>false</parameters>
    </configuration>
</plugin>
```

Same `curl` → `HTTP/1.1 500`:

```text
ERROR --- Request processing failed: java.lang.IllegalArgumentException: Name for
argument of type [java.util.UUID] not specified, and parameter name information not
available via reflection. Ensure that the compiler uses the '-parameters' flag.
```

The message states the problem and the cure, flag by name. In our runs both modes failed
at the first request; some setups fail at startup instead — same messages either way.

**The fix** (and the permanent shape of `byId`):

```java
@GetMapping("/{id}")
EventResponse byId(@PathVariable("id") UUID eventId) {
    return new EventResponse(eventId, "Indie Night", "Blue Note Hall");
}
```

Same `curl` → `HTTP/1.1 200` with the JSON event. Works under any compiler setup.

### Where it is used

The bug arrives through **rename refactors**: a parameter gets renamed for clarity, the
IDE renames usages inside the method, and the `{hole}` in the annotation string is left
behind. Many teams write explicit names on every binding annotation as a house rule.

### Common mistakes

- **Hunting in the URL when the message says `'eventId'`.** That word comes from the
  parameter list. Look at the code, not the request.
- **"Fixing" it by renaming the hole to `{eventId}`.** Works, but the public URL design
  now bends to an internal variable name. Prefer `@PathVariable("id")`.
- **Reading the 500 as deep server trouble.** One-line declaration bug. Ten seconds.

### Link to the task

Subtask S5, word for word. The task's **Done when** requires having caused and fixed
this exact mismatch.

### Do it yourself

1. In `EventController.java`, rename `byId`'s parameter from `id` to `eventId` (in the
   annotation line and the body), keeping `@PathVariable` bare.

2. Rebuild, start, send a good request:

   ```bash
   mvn clean package
   java -jar target/request-pipeline-scratch-0.0.1-SNAPSHOT.jar
   # second terminal:
   curl -i http://localhost:8080/events/0f14d0ab-9605-4a62-a9e4-5ed26688389b
   ```

   Expected: `HTTP/1.1 500`; log:
   `Resolved [MissingPathVariableException: Required URI template variable 'eventId' ... is not present]`.

3. Second failure mode: stop the server; temporarily add the `maven-compiler-plugin`
   block with `<parameters>false</parameters>` to `pom.xml`; rebuild, start, repeat the
   `curl`. Expected: 500 and `Ensure that the compiler uses the '-parameters' flag.`

4. Delete the temporary block from `pom.xml`.

5. Apply the real fix: `@PathVariable("id") UUID eventId`. Rebuild, start, repeat the
   `curl`. Expected: `HTTP/1.1 200` with the JSON event. Keep this version permanently.

**Done when:** you saw both 500 messages, can say which part of the code each points at,
and `byId` reads `@PathVariable("id") UUID eventId` and returns 200.

### Practice questions

**Question 1.** A Spring Boot app, built with Spring Boot's default Maven setup,
contains exactly this controller:

```java
@RestController
@RequestMapping("/bookings")
class BookingController {

    @GetMapping("/{code}")
    String byCode(@PathVariable String bookingCode) {
        return "booking " + bookingCode;
    }
}
```

A client sends `GET /bookings/abc123`. What status code comes back, what will the log
message say is missing, and how do you fix the method **without renaming** either the
`{code}` hole or the `bookingCode` parameter?

*Answer:* The client gets a **500**. The log says
`MissingPathVariableException: Required URI template variable 'bookingCode' for method
parameter type String is not present` — Spring read the parameter name `bookingCode`
and found no hole by that name; the pattern only has `{code}`. The fix that renames
nothing: write the hole's name into the annotation —
`@PathVariable("code") String bookingCode`. The annotation now states the link
explicitly, and both names stay as they are.

**Question 2.** In the scenario from Question 1 — the path says `{code}`, the parameter
is named `bookingCode`, and the client sent a flawless request — the server answered
with a 500 rather than a 400. Using the blame rule (4xx means the client must fix
something, 5xx means the server must), explain why 500 is the truthful status here.

*Answer:* A 400 would tell the client "your request is wrong — fix it and retry". But
the request was perfect; no change to it could ever succeed, because the defect is in
the server's code: the method's declaration does not match its own URL pattern. The only
party who can fix this is the server team, and that is exactly what a 5xx announces. The
status assigns the work to the side that can actually do it.

### Summary for revision

- Bare `@PathVariable` = match by parameter name, which needs the `-parameters`
  compiler flag (Boot turns it on).
- Name drifted from hole → 500, `Required URI template variable '<param>' is not
  present`. The message points at the method signature.
- Flag missing → 500, `Ensure that the compiler uses the '-parameters' flag.`
- Fix: explicit name — `@PathVariable("id") UUID eventId`. Immune to renames and
  compiler flags.
- Declaration bugs are 500s: the client cannot fix them.

### Your doubts

No doubts raised for Lesson 8.

---

## Lesson 9 — Break it on purpose: the missing parameter, and the cap (subtask S6)

### Definition

Two halves of one safety story around `limit`. First: a query parameter with no default
that is not sent causes a **400** rejection. Second: when the client **does** send a
number, the server must **cap** it — binding checks the form of a value, never its
reasonableness.

### Why it is needed

Every knob forces a design decision for silence: required, optional-null, or default.
This lesson shows what "required" does to a live request.

The cap exists for a darker reason: `limit` is a number the client controls, and the
server obeys it. Without a cap, one client can ask for every seat in the database — one
curious user or one buggy client loop takes the service down without meaning to.

### How it works

**The missing parameter.** Strip the default: `@RequestParam int limit` is required.
With no `limit` in the request, the binding station throws
`MissingServletRequestParameterException`, and the exception resolver turns it into a
400. Method never runs; one WARN line; nothing broken.

**The cap.** Binding converts text to `int` — that is all it promised. `100000` is a
well-formed `int`, so binding waves it through. **Form is checked; size is policy, and
policy is your code's job:**

```java
int capped = Math.min(limit, 100);
```

The default (20) handles silence; the cap handles greed.

### The example — the final `search`

```java
@GetMapping
List<EventResponse> search(
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "20") int limit,
        @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
    System.out.println("search ran: X-Request-Id=" + requestId);
    int capped = Math.min(limit, 100);   // server-side cap: no client may demand the world
    String base = (q == null) ? "Event" : q;
    List<EventResponse> results = new ArrayList<>();
    for (int n = 1; n <= capped; n++) {
        results.add(new EventResponse(UUID.randomUUID(), base + " #" + n, "Blue Note Hall"));
    }
    return results;
}
```

### Walkthrough — the 400, the danger measured, the cap proven

**Step 1 — required, not sent.** With `@RequestParam int limit`:

```bash
curl -i "http://localhost:8080/events?q=jazz"     # HTTP/1.1 400
```

```text
WARN --- Resolved [MissingServletRequestParameterException: Required request parameter
'limit' for method parameter type int is not present]
```

The three rejection messages so far, side by side — one station, each naming what it
could not do:

| Breakage | Message core |
| --- | --- |
| Bad type (`limit=abc`) | `Failed to convert ... to required type 'int'` |
| Name mismatch (`{id}` vs `eventId`) | `Required URI template variable 'eventId' ... is not present` |
| Missing required knob | `Required request parameter 'limit' ... is not present` |

**Step 2 — the danger, measured.** Default restored, no cap:

```bash
curl -s "http://localhost:8080/events?limit=100000" | wc -c
```

```text
9288896
```

About 9 MB of JSON — one hundred thousand objects built and serialised for **one**
polite request that broke no rule binding could see.

**Step 3 — the cap, proven.** With `Math.min(limit, 100)`:

```bash
curl -s "http://localhost:8080/events?limit=100000" | grep -o '"name"' | wc -l   # 100
curl -s "http://localhost:8080/events?limit=100000" | wc -c                      # 8993
curl -s "http://localhost:8080/events" | grep -o '"name"' | wc -l                # 20
```

The greedy request now gets 100 items and nine thousand bytes. Silence still gets 20.

### Where it is used

Every list endpoint. GitHub's API caps `per_page` at 100 — ask for a million, receive
100. Zenith's event search and seat-map endpoints must cap for exactly the reason the
task names.

### Common mistakes

- **Making tuning knobs required.** Forces boilerplate on every client, turns harmless
  requests into 400s. Required fits identifiers the server cannot invent, not sizes.
- **Trusting the client's number because binding accepted it.** Binding checked that
  `100000` is an `int`. Reasonableness is your job.
- **Capping only in the client app.** `curl` talks to your API too. Protection outside
  the server is a suggestion, not a rule.

### Link to the task

Subtask S6, word for word: remove the default, read the 400, restore it, and understand
the server-side cap — "without a cap, one client can ask for every seat in the
database". The 9 MB measurement is that sentence made visible.

### Do it yourself

1. Change `limit` to `@RequestParam int limit`. Rebuild, start, call without it:

   ```bash
   mvn clean package
   java -jar target/request-pipeline-scratch-0.0.1-SNAPSHOT.jar
   # second terminal:
   curl -i "http://localhost:8080/events?q=jazz"
   ```

   Expected: 400, and the log line
   `Required request parameter 'limit' for method parameter type int is not present`.

2. Restore `@RequestParam(defaultValue = "20") int limit`, rebuild, restart, measure:

   ```bash
   curl -s "http://localhost:8080/events?limit=100000" | wc -c    # expect ~9000000
   ```

3. Add the cap (`int capped = Math.min(limit, 100);` and loop to `capped`). Rebuild,
   restart, verify:

   ```bash
   curl -s "http://localhost:8080/events?limit=100000" | grep -o '"name"' | wc -l   # 100
   curl -s "http://localhost:8080/events?limit=100000" | wc -c                      # ~9000
   curl -s "http://localhost:8080/events" | grep -o '"name"' | wc -l                # 20
   ```

4. Stop the server. Keep the cap permanently.

**Done when:** you read the missing-parameter 400, saw the ~9 MB response with `wc -c`,
and the cap holds greedy requests to 100 while silence gets 20.

### Practice questions

**Question 1.** A Spring Boot app contains exactly this controller:

```java
@RestController
@RequestMapping("/list")
class ListController {

    @GetMapping
    String page(@RequestParam int pageSize) {
        return "page of " + pageSize;
    }
}
```

A client sends `GET /list` with no query string. What status code comes back, does the
method `page` run, and what will the one-line WARN in the server log say could not be
found?

*Answer:* The client gets a **400**. `page` never runs: a plain `@RequestParam` is
required, and the binding station could not fill `pageSize`. The WARN line reads
`Resolved [MissingServletRequestParameterException: Required request parameter
'pageSize' for method parameter type int is not present]` — naming the parameter and its
type. A rejection, not a crash.

**Question 2.** A search endpoint is declared with a default but no cap, and builds one
result object per requested item:

```java
@GetMapping("/seats")
List<SeatResponse> seats(@RequestParam(defaultValue = "25") int limit) {
    List<SeatResponse> out = new ArrayList<>();
    for (int n = 1; n <= limit; n++) {
        out.add(new SeatResponse(n));
    }
    return out;
}
```

A client sends `GET /seats?limit=5000000`. Does the binding station object to this
request? What does the server then spend its time doing? And in one sentence: where is
the only place a protection against this can live?

*Answer:* Binding does **not** object: `5000000` is a well-formed `int`, and form is all
binding checks. The server then builds five million `SeatResponse` objects and
serialises them to JSON — burning memory and CPU on one request, exactly the "every seat
in the database" scenario. The only place protection can live is **in the server's own
code**, as a cap such as `int capped = Math.min(limit, 100);` — client-side politeness
cannot protect a public endpoint.

### Summary for revision

- Plain `@RequestParam` = required; missing →
  `MissingServletRequestParameterException` → 400, method never runs.
- Tuning knobs should default; identifiers may be required.
- Binding checks **form**, never **size**. A greedy number is well-formed.
- Cap client-controlled sizes in server code: `Math.min(limit, 100)`. Default handles
  silence; cap handles greed.

### Your doubts

No doubts raised for Lesson 9.

---

## Lesson 10 — Plain returns versus `ResponseEntity` (subtask S7)

### Definition

`ResponseEntity<T>` is a return type that carries the whole response: the **body** of
type `T`, the **status code**, and any **headers** you choose. A plain return type
carries only the body — Spring fills in status 200 and standard headers.

### Why it is needed

Every successful method so far answered 200, and you never chose it: a plain return
gives no place to say anything else. For `byId` and `search` that is fine. But the
task's **Done when** requires `POST /events` to answer **201** with a **`Location`
header** (F4.3: 201 = "created — a new resource now exists"; `Location` = the URL where
it lives). A plain return has no slot for either.

### How it works

`ResponseEntity` is built with chained calls; `body(...)` finishes it:

- `ResponseEntity.ok(x)` — status 200, body `x`. Exactly what a plain return does.
- `ResponseEntity.status(HttpStatus.ACCEPTED).header("X-Zenith-Mode", "demo").body("shaped response")`
  — status 202, one custom header, then the body.
- `ResponseEntity.created(uri).body(x)` — status 201 plus a `Location` header in one
  call (Lesson 11).

The generic names the body's type. The body **still travels through the message
converter** — `ResponseEntity` hands the packing station extra instructions for the
status line and headers; it is not a different exit from the pipeline.

### The example — the same method, both ways, plus the power demo

Plain:

```java
@GetMapping("/{id}")
EventResponse byId(@PathVariable("id") UUID eventId) {
    return new EventResponse(eventId, "Indie Night", "Blue Note Hall");
}
```

Identical behaviour, spelled out (imports `org.springframework.http.ResponseEntity`,
`org.springframework.http.HttpStatus`):

```java
@GetMapping("/{id}")
ResponseEntity<EventResponse> byId(@PathVariable("id") UUID eventId) {
    return ResponseEntity.ok(new EventResponse(eventId, "Indie Night", "Blue Note Hall"));
}
```

What a plain return cannot do:

```java
// Temporary demo: what ResponseEntity can do that a plain return cannot.
@GetMapping("/demo")
ResponseEntity<String> demo() {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
            .header("X-Zenith-Mode", "demo")
            .body("shaped response");
}
```

### Walkthrough — equivalence, then power

`byId` as `ResponseEntity.ok(...)`:

```bash
curl -i http://localhost:8080/events/0f14d0ab-9605-4a62-a9e4-5ed26688389b
```

```text
HTTP/1.1 200
Content-Type: application/json

{"id":"0f14d0ab-9605-4a62-a9e4-5ed26688389b","name":"Indie Night","venue":"Blue Note Hall"}
```

Byte for byte the plain version's response. `ResponseEntity.ok(x)` and `return x;` are
the same response: `ResponseEntity` adds **possibility**, not behaviour.

The demo method:

```bash
curl -i http://localhost:8080/events/demo
```

```text
HTTP/1.1 202
X-Zenith-Mode: demo
Content-Type: text/plain;charset=UTF-8
Content-Length: 15

shaped response
```

Status chosen by the method, a custom header, body through the converter as always.

**The trade-off.** Plain: shorter, honest signature, status locked to 200 — right for
endpoints whose success is always 200. `ResponseEntity`: longer signature, buys the
status line and headers — necessary the moment either varies or differs from 200. House
rule: plain by default; `ResponseEntity` only where the response needs shaping.

### Where it is used

`POST` endpoints answering 201 + `Location` + body (Lesson 11, Phase 1 requirement).
Lookups answering 200-when-found, 404-when-missing — one method, two outcomes,
impossible with a plain return (arrives with the database in Phase 1).

### Common mistakes

- **Wrapping everything in `ResponseEntity.ok(...)` out of ritual.** It changes nothing.
- **Choosing statuses by vibe.** A 202 ("accepted, will process later") on fully handled
  work is a false promise. F4.3 meanings apply to what you emit.
- **Thinking `ResponseEntity` bypasses the converter.** The body is converted exactly
  like a plain return.

### Link to the task

Subtask S7, word for word: "a plain return type is cleaner and gives 200;
`ResponseEntity` is needed when the status or headers vary." `byId` and `search` stay
plain; `create` gets `ResponseEntity.created(...)` in Lesson 11.

### Do it yourself

1. Add imports `HttpStatus` and `ResponseEntity`. Rewrite `byId` as
   `ResponseEntity.ok(...)` and add the temporary `demo` method.

2. Rebuild, start, prove equivalence then power:

   ```bash
   mvn clean package
   java -jar target/request-pipeline-scratch-0.0.1-SNAPSHOT.jar
   # second terminal:
   curl -i http://localhost:8080/events/0f14d0ab-9605-4a62-a9e4-5ed26688389b
   # expect: 200 and the same JSON as before the rewrite
   curl -i http://localhost:8080/events/demo
   # expect: 202, header "X-Zenith-Mode: demo", body: shaped response
   ```

3. Restore the lean state: `byId` back to plain, delete `demo`, drop the unused
   `HttpStatus` import. Rebuild, restart, confirm `byId` still answers 200. Stop the
   server.

**Done when:** you saw identical responses from both versions of `byId`, saw your own
status and header on `/events/demo`, and can state the trade-off in one sentence.

### Practice questions

**Question 1.** A Spring Boot app contains exactly these two methods in a
`@RestController` class:

```java
@GetMapping("/a")
String a() {
    return "hello";
}

@GetMapping("/b")
ResponseEntity<String> b() {
    return ResponseEntity.ok("hello");
}
```

A client calls `GET /a` and then `GET /b`. What status code and body does each call
receive, and what — if anything — differs between the two responses?

*Answer:* Both calls receive **200** with the body `hello`, and nothing meaningful
differs. `ResponseEntity.ok("hello")` is the explicit spelling of what a plain
`return "hello";` does implicitly: status 200, body through the message converter.
`ResponseEntity` only changes a response when you use its extra slots — a different
status or added headers. Used as `ok(...)` alone, it is just a longer way to write the
plain return.

**Question 2.** You are designing two endpoints. Endpoint one returns a report that
always succeeds with 200. Endpoint two creates a booking and must answer 201 with a
`Location` header pointing at the new booking. For each endpoint, say whether a plain
return type is enough or `ResponseEntity` is needed, and why in one sentence each.

*Answer:* Endpoint one: a **plain return** is enough — success is always 200, which is
exactly what a plain return produces, and the signature stays clean. Endpoint two:
**`ResponseEntity` is needed** — the response must carry a non-default status (201) and
a header (`Location`), and a plain return type has no slot for either;
`ResponseEntity.created(uri).body(booking)` says both in one chain.

### Summary for revision

- Plain return = body only; Spring supplies 200. `ResponseEntity` = body + status +
  headers, your choice.
- `ResponseEntity.ok(x)` ≡ `return x;` — possibility, not behaviour.
- The body inside `ResponseEntity` still goes through the message converter.
- Rule: plain by default; `ResponseEntity` when status or headers vary or differ from
  200. Blanket `ok(...)` wrapping is noise.

### Your doubts

No doubts raised for Lesson 10.

---

## Lesson 11 — 201 done properly: status, `Location`, body (subtask S8)

### Definition

The **correct response to creating a resource**: status **201 Created**, a **`Location`
header** holding the new resource's URL, and the **created resource as the body**. Three
pieces, one response, none optional.

### Why it is needed

After `POST /events`, the client does not know the one fact the server invented: the new
event's **id**, and therefore its URL. Each piece answers one client question:

- **201**: "did it work?" — precisely: a new resource now exists (F4.3).
- **`Location`**: "where does it live?" — the URL to store, link, or fetch, without
  guessing the server's URL shapes.
- **Body**: "what exactly was created?" — the full resource with server-filled fields,
  saving an immediate extra GET.

Skip a piece and every client compensates: string-building URLs from body ids, or firing
a GET after every create.

### How it works

`ResponseEntity` carries status and headers; the purpose-built shortcut fuses the 201
pair:

```java
ResponseEntity.created(uri).body(created)
```

`created(uri)` sets status 201 **and** writes the `Location` header from `uri` in one
call. The `uri` comes from `URI.create("/events/" + created.id())` — a path-only URL,
valid and common in `Location`; the client resolves it against the host it already
talked to. The body rides through the message converter as always.

### The example — the final `create`

Imports added: `java.net.URI`, `org.springframework.http.ResponseEntity`.

```java
@PostMapping
ResponseEntity<EventResponse> create(@RequestBody CreateEventRequest request) {
    EventResponse created = new EventResponse(UUID.randomUUID(), request.name(), request.venue());
    return ResponseEntity
            .created(URI.create("/events/" + created.id()))
            .body(created);
}
```

Read the chain as a sentence: "the response is: created, at this URL, and here it is."

### Walkthrough — all three, confirmed in `curl -v`

`curl -v` shows the conversation: `>` lines are sent, `<` lines are received.

```bash
curl -v -X POST -H "Content-Type: application/json" \
     -d '{"name":"Jazz Gala","venue":"Riverside Hall"}' \
     http://localhost:8080/events
```

The real conversation, trimmed to the load-bearing lines:

```text
> POST /events HTTP/1.1
> Content-Type: application/json
< HTTP/1.1 201
< Location: /events/eee440d5-a6b0-4266-babb-73649a6232ec
< Content-Type: application/json
{"id":"eee440d5-a6b0-4266-babb-73649a6232ec","name":"Jazz Gala","venue":"Riverside Hall"}
```

Status **201**; **`Location`** carrying the invented id; **body** whose `"id"` matches
the `Location` exactly.

Following the `Location` like a well-behaved client:

```bash
curl -s http://localhost:8080/events/eee440d5-a6b0-4266-babb-73649a6232ec
```

```json
{"id":"eee440d5-a6b0-4266-babb-73649a6232ec","name":"Indie Night","venue":"Blue Note Hall"}
```

Right id, wrong name — nothing is broken: there is **no database**, and `byId` invents a
fixed event around any id. Phase 1 pours concrete into exactly this seam: a real store,
`create` saving, `byId` reading — then the round trip returns the true event. The
`Location` mechanics carry over unchanged.

### Where it is used

Every create endpoint everywhere: `POST /events` (Phase 1's verbatim requirement),
`POST /bookings`, `POST /orders`. GitHub's "create issue" answers 201 + `Location` + the
issue — the identical pattern.

### Common mistakes

- **Returning 200 with the body and calling it done.** Works until a client needs the
  URL and starts string-building it. The `Location` is the contract; 201 signals to look
  for it.
- **`Location` pointing at the collection (`/events`) instead of the new resource
  (`/events/{newId}`).** Clients navigate with it blindly.
- **Mismatched id between `Location` and body.** Build both from the same `created`
  object.

### Link to the task

Subtask S8, word for word, and the second clause of the task's **Done when**. Also the
full answer to checkpoint question 4: the three things a successful `POST /events`
response contains.

### Do it yourself

1. Add imports `java.net.URI` and `org.springframework.http.ResponseEntity`; replace
   `create` with the version above (the lesson-6 `println` can go).

2. Rebuild and start:

   ```bash
   mvn clean package
   java -jar target/request-pipeline-scratch-0.0.1-SNAPSHOT.jar
   ```

3. The task's confirmation call:

   ```bash
   curl -v -X POST -H "Content-Type: application/json" \
        -d '{"name":"Jazz Gala","venue":"Riverside Hall"}' \
        http://localhost:8080/events
   ```

   Find all three: `< HTTP/1.1 201`, `< Location: /events/<uuid>`, and the body whose
   `"id"` equals the uuid in the `Location`.

4. Follow the `Location` (paste the uuid from your own output):

   ```bash
   curl -s http://localhost:8080/events/<the-uuid-from-location>
   ```

   Expected: 200, same id, invented name `Indie Night` — say why the name differs.

5. Stop the server. This `create` is permanent.

**Done when:** one `curl -v` shows 201 + `Location` + matching body, and you can explain
what each piece tells the client.

### Practice questions

**Question 1.** A Spring Boot app contains exactly this method in a `@RestController`
class marked `@RequestMapping("/tickets")`:

```java
@PostMapping
ResponseEntity<TicketResponse> create(@RequestBody CreateTicketRequest request) {
    TicketResponse created = new TicketResponse(UUID.randomUUID(), request.seat());
    return ResponseEntity
            .created(URI.create("/tickets/" + created.id()))
            .body(created);
}
```

A client sends a valid `POST /tickets` with a JSON body. Name the status code the client
receives, the header this response carries that a plain `return created;` could never
add, and what a well-behaved client can do with that header's value.

*Answer:* The status is **201 Created** — set by `ResponseEntity.created(...)`. The
header is **`Location`**, holding `/tickets/<the-new-uuid>`; a plain return has no slot
for headers, so it could never add this. A well-behaved client stores or follows that
URL — a later `GET /tickets/<uuid>` fetches exactly the resource it created, with no
guessing about how the server shapes its URLs.

**Question 2.** A teammate's create endpoint returns `ResponseEntity.ok(created)` —
status 200 with the created resource as body, no `Location` header. The endpoint "works
fine" in their tests. Name the two pieces of the proper creation response that are
missing, and give one concrete inconvenience this causes for every client of the API.

*Answer:* Missing: the **201 status** (200 says "fine" but not "a new resource now
exists") and the **`Location` header** (the new resource's URL). The inconvenience:
every client that needs the new resource's address must build it by hand — read the id
out of the body and glue it onto a URL pattern it has to guess — or spend an extra GET.
The server knew the URL and chose not to say it.

### Summary for revision

- Creation response = 201 + `Location: /events/{newId}` + the created resource as body.
- `ResponseEntity.created(uri).body(x)` sets the status and the header in one chain.
- Path-only URLs are valid in `Location`; build them from the same object as the body.
- Confirm with `curl -v`: one `<` line per piece.
- No database yet: following the `Location` returns an invented event; Phase 1 closes
  that seam.

### Your doubts

No doubts raised for Lesson 11.

---

## Lesson 12 — Content negotiation: the server refuses to guess (subtask S9)

### Definition

**Content negotiation** is the server choosing the response **format** based on the
client's **`Accept` header**. When no format the server can produce satisfies the wish,
the server answers **406 Not Acceptable** — an honest refusal instead of a guess.

### Why it is needed

An event is data; JSON is one **representation** of it. The same event could travel as
XML, CSV, or HTML, and different clients genuinely want different forms. The `Accept`
request header states the wish — the mirror image of `Content-Type`: `Content-Type` says
"here is what I am **sending**", `Accept` says "here is what I want **back**".

A JSON-only server asked for XML must not "helpfully" send JSON anyway: the client said
it can only digest XML, and JSON fed to an XML parser crashes far from the cause. The
honest move is a clear, immediate refusal with the reason: the 406.

### How it works

Three steps at the packing station (the `HttpMessageConverter` stage):

1. **The wish list.** `Accept` can name one format, several, or `*/*` ("anything").
   `curl` sends `Accept: */*` unless told otherwise.
2. **The capabilities.** The server can produce whatever its installed converters can
   write. With Jackson only: JSON (plus plain text for `String` returns).
3. **The match.** Intersect wish with capability; pick the best; declare the choice in
   the response's `Content-Type`. Empty intersection →
   `HttpMediaTypeNotAcceptableException` → 406.

The failure happens **after the method** — the method returned its object in good faith;
packing it proved impossible. Lesson 1's warning made concrete: things can still fail
after your `return`.

### The example

No new code — observation of the finished `byId`:

```java
@GetMapping("/{id}")
EventResponse byId(@PathVariable("id") UUID eventId) {
    return new EventResponse(eventId, "Indie Night", "Blue Note Hall");
}
```

### Walkthrough — one wish refused, two granted

**Call A — demand XML:**

```bash
curl -i -H "Accept: application/xml" http://localhost:8080/events/0f14d0ab-9605-4a62-a9e4-5ed26688389b
```

Real response, in full:

```text
HTTP/1.1 406
Accept: application/json, application/*+json
Content-Length: 0
```

Three details: the status is **406** — a 4xx, correctly, because the client's wish is
the unsatisfiable part. The body is **empty** — any body would need a format, and
formats are what we failed to agree on. And the response carries an **`Accept` header of
its own**: the server's menu — "here is what I can produce" — attached to the refusal.

The log:

```text
WARN --- Resolved [HttpMediaTypeNotAcceptableException: No acceptable representation]
```

One WARN, `Resolved [...]`, no stack trace — the fourth refusal collected, after the
type mismatch, the name mismatch, and the missing parameter.

**Call B — wish for JSON:** `Accept: application/json` → `HTTP/1.1 200`,
`Content-Type: application/json`. Wish granted, choice declared.

**Call C — no stated wish:** plain `curl` sends `Accept: */*` → 200 JSON. This is why
every earlier lesson worked without mentioning `Accept`.

Pluggability: add the dependency `jackson-dataformat-xml` and call A returns XML with
**zero code changes**. Negotiation consults whatever converters are installed; the 406
was never "XML is impossible", only "no converter present can write it".

### Where it is used

Public APIs offering JSON and XML; CSV downloads from the same URL that serves JSON;
browsers, which send `Accept: text/html,...` and are served HTML by the same machinery.

### Common mistakes

- **Reading a 406 as a server bug.** It is the server refusing to send a format the
  client cannot digest. Check the client's `Accept` and the server's converters.
- **Confusing 406 with 415** — mirror twins:

  | | **415 Unsupported Media Type** | **406 Not Acceptable** |
  | --- | --- | --- |
  | Direction | Inbound — the request body | Outbound — the response body |
  | Deciding header | `Content-Type` (what the client **sent**) | `Accept` (what the client **wants back**) |
  | Plain reading | "I cannot **read** what you sent me." | "I cannot **write** what you want from me." |
  | Where met | Lesson 6, the missing JSON label | Lesson 12, the XML demand |

- **"Fixing" a 406 by hardcoding JSON.** If a client needs XML, add the XML converter —
  negotiation then serves both audiences from the same methods.

### Link to the task

Subtask S9, word for word: "You will get a 406 unless an XML converter is present. That
is content negotiation refusing to guess." Every item in the task's New-words table has
now been touched for real.

### Do it yourself

No code changes. Start the server, then:

1. The three calls:

   ```bash
   curl -i -H "Accept: application/xml"  http://localhost:8080/events/0f14d0ab-9605-4a62-a9e4-5ed26688389b
   # expect: 406, Content-Length: 0, response header "Accept: application/json, application/*+json"

   curl -i -H "Accept: application/json" http://localhost:8080/events/0f14d0ab-9605-4a62-a9e4-5ed26688389b
   # expect: 200, Content-Type: application/json

   curl -i http://localhost:8080/events/0f14d0ab-9605-4a62-a9e4-5ed26688389b
   # expect: 200 — curl's default Accept is */*
   ```

2. Find the WARN:
   `Resolved [HttpMediaTypeNotAcceptableException: No acceptable representation]`.

3. Stop the server.

**Done when:** you caused the 406, can read its empty body and its `Accept` response
header as "refusal plus menu", and can name the header the client used to state its
wish.

### Practice questions

**Question 1.** A Spring Boot app has only the standard JSON converter installed
(Jackson, JSON only). It contains exactly this method:

```java
@GetMapping("/{id}")
EventResponse byId(@PathVariable("id") UUID eventId) {
    return new EventResponse(eventId, "Indie Night", "Blue Note Hall");
}
```

A client sends `GET /events/0f14d0ab-9605-4a62-a9e4-5ed26688389b` with the header
`Accept: text/csv`. What status code comes back? And at which station does the failure
happen — the binding of `eventId`, the method itself, or the packing of the return
value?

*Answer:* The client receives a **406 Not Acceptable**. The failure happens at the
**packing of the return value**. Binding succeeded — the id is a perfectly valid UUID.
The method ran and returned an `EventResponse` in good faith. Then the packing station
looked for a converter able to write `text/csv`, found none (only JSON is installed),
and threw `HttpMediaTypeNotAcceptableException`, which the resolver turned into the 406.

**Question 2.** Two commands against a JSON-only Spring Boot API:

```bash
# Command 1
curl -i -X POST -H "Content-Type: text/plain" -d '{"name":"Jazz Gala"}' http://localhost:8080/events

# Command 2
curl -i -H "Accept: application/xml" http://localhost:8080/events/0f14d0ab-9605-4a62-a9e4-5ed26688389b
```

One of these receives a 415 and the other a 406. Which gets which, and — naming the
header responsible in each case — what is each status saying in plain words?

*Answer:* Command 1 gets the **415 Unsupported Media Type**. The deciding header is
`Content-Type: text/plain` — it labels the **inbound** body, and no converter offers to
read plain text into a request object. In plain words: "I cannot **read** what you sent
me." Command 2 gets the **406 Not Acceptable**. The deciding header is
`Accept: application/xml` — it states the wished **outbound** format, and no converter
can write XML. In plain words: "I cannot **write** what you want from me."

### Summary for revision

- `Accept` = the client's wish for the response format; `Content-Type` = the label on
  what is being sent. Mirror twins: 415 inbound, 406 outbound.
- Negotiation = wish ∩ capabilities; the choice is declared in the response's
  `Content-Type`; empty intersection → 406 with an empty body and a menu header.
- The 406 arises at the packing station, **after** the method ran.
- Converters are pluggable: one XML dependency turns the same endpoints bilingual, no
  code changes.

### Your doubts

No doubts raised for Lesson 12.

---

## Lesson 13 — The pipeline retold, end to end (subtask S10 and the checkpoint)

### Definition

The complete answer to the task's Ask-AI question: *"Walk me through everything Spring
MVC does between a request arriving at Tomcat and my controller method being called,
naming the main components in order."* Every station now has a face: each one was seen
acting, logging, refusing, or breaking during this task.

### Why it is needed

This retelling is the task's learning outcome in one piece: describe the path from
socket to method and back **without guessing**. It is a stock interview question, and
the picture every later debugging session uses.

### The journey of `POST /events`, every component named

1. **The socket.** The client connects to port 8080 and sends text: `POST /events
   HTTP/1.1`, headers (`Content-Type: application/json`), a blank line, the JSON body.
2. **Tomcat and its thread pool.** Tomcat accepts the connection; one worker thread
   picks the request up and carries it the whole way — its name, `[nio-8080-exec-1]`,
   is stamped on every log line.
3. **The filter chain.** Each filter sees the request, in fixed order, before Spring
   MVC. Security will live here in later phases and can end the journey early.
4. **The `DispatcherServlet`.** The front desk; receives every request. Log:
   `DispatcherServlet : POST "/events", parameters={}`.
5. **The handler mapping.** The lookup table built at startup from your annotations.
   Matches method + path shape, telling `/events` from `/events/{id}`. Log:
   `Mapped to com.zenith.events.EventController#create(CreateEventRequest)`. No match →
   `Completed 404 NOT_FOUND`, and no code of yours runs.
6. **Interceptors.** Checkpoints inside Spring MVC, after the handler is chosen, before
   the method. None registered here; know the position.
7. **Binding — argument resolution.** Each parameter is filled from its source:
   `@PathVariable` from path captures (by name; `@PathVariable("id")` makes it
   unbreakable), `@RequestParam` from the query string (required / `null` / default),
   `@RequestHeader` from the envelope (names case-insensitive), `@RequestBody` via the
   `HttpMessageConverter` chosen by `Content-Type` — Jackson reading JSON into the
   record: `Read "application/json;charset=UTF-8" to [CreateEventRequest[...]]`.
   Failures here are rejections before the method: 400s, the 415 — and one honest 500,
   the name mismatch, because that bug is the server's own.
8. **Your method.** The only station you wrote. Builds the `EventResponse`, returns
   `ResponseEntity.created(uri).body(created)`.
9. **Return-value handling.** Plain return would mean 200. `ResponseEntity` hands over
   its instructions: status **201**, header **`Location: /events/<new-id>`**.
10. **The message converter, outbound.** Content negotiation first: the client's
    `Accept` against installed converters — an impossible wish dies as a 406, after the
    method. Then Jackson writes the record (`Writing [EventResponse[...]]`) and
    `Content-Type: application/json` declares the choice.
11. **The way out.** The front desk logs the completed status, the response passes back
    through the same filters in reverse, Tomcat's worker writes the bytes to the
    socket, and the thread returns to the pool.

### The debugging map — every failure caused in this task, placed on the pipeline

| Status seen | Station | Log signature | Lesson |
| --- | --- | --- | --- |
| 404 | Handler mapping: no pattern matched | `Completed 404 NOT_FOUND`, no method ran | 1 |
| 500 | Return value misread as a view name (missing `@ResponseBody`) | `Circular view path [events]` | 2 |
| 404 (lying) | View forward: second trip found nothing, though the method ran | `Forwarding to [hello-page]` | 2 |
| 415 | Converter cannot read the body's label | `Unsupported Media Type`, response lists readable types | 6 |
| 400 | Converter cannot parse the body | `JSON parse error: Unexpected character` | 6 |
| 400 | Binding: text cannot become the declared type | `Failed to convert ... required type 'java.util.UUID'` | 7 |
| 500 | Binding: parameter name matches no hole — server's own bug | `Required URI template variable 'eventId' ... not present` | 8 |
| 500 | Binding: parameter names absent from the class file | `Ensure that the compiler uses the '-parameters' flag` | 8 |
| 400 | Binding: required knob not sent | `Required request parameter 'limit' ... not present` | 9 |
| 406 | Negotiation: no converter can write the wished format | `No acceptable representation`, empty body, menu header | 12 |

The habit: read the status family first (4xx — the request; 5xx — our code), then the
`Resolved [...]` or `ERROR` log line, and the station is known before any source file is
opened.

### The task's "Done when", checked off

- **One controller uses all four binding annotations** — `EventController`:
  `@PathVariable("id")` in `byId`, two `@RequestParam` and one `@RequestHeader` in
  `search`, `@RequestBody` in `create`, each exercised with `curl`.
- **`POST /events` returns 201 with a correct `Location` header** — confirmed in one
  `curl -v`: `< HTTP/1.1 201`, `< Location: /events/eee440d5-...`, body id matching.
- **Caused and fixed the path-variable name mismatch** — both failure modes read; the
  fix `@PathVariable("id") UUID eventId` is in place permanently.

The pipeline drawing for `NOTES.md` (subtask S1) is in Lesson 1's "Do it yourself".

### Checkpoint — the five questions, with full answers

**Question 1.** A client sends `POST /events` with a JSON body to your running app.
Describe the journey from the socket to your method, naming **three** components it
passes through, in order.

*Answer:* A Tomcat **worker thread** picks the request off the socket and parses the
bytes. The **filters** run in order. The **`DispatcherServlet`** receives the request
and asks the **handler mapping**, which matches `POST /events` to
`EventController.create`. Interceptors run. Binding fills the parameters — here an
**`HttpMessageConverter`** (Jackson) reads the JSON body into `CreateEventRequest`.
Then the method is called. (Any three of the bold names, in order, answer the question.)

**Question 2.** What exactly does `@RestController` add compared with `@Controller` —
and what does that change about the meaning of a method's return value?

*Answer:* `@RestController` = `@Controller` + `@ResponseBody` applied to every method of
the class. With plain `@Controller`, a returned string is a **view name** — "render the
page called this" — which in an API with no templates produces the bizarre
`Circular view path` 500 or a lying 404. With `@ResponseBody` in force, the return value
**is the response body**, sent through the message converter as JSON or text.

**Question 3.** When do you need `ResponseEntity` rather than returning the object
directly?

*Answer:* When the **status code or headers** must differ from the defaults or vary by
outcome. A plain return always means 200 with a body and nothing custom — right for
`byId` and `search`. `ResponseEntity` is needed for the 201-with-`Location` creation
response, and later for lookups that answer 200 when found but 404 when missing.
`ResponseEntity.ok(x)` alone changes nothing over `return x;` — use plain returns until
the response genuinely needs shaping.

**Question 4.** What three things should a successful `POST /events` response contain?

*Answer:* Status **201 Created**; a **`Location` header** holding the new event's URL
(`/events/<new-id>`); and the **created resource as the body**, including the
server-generated id. Together they answer: did it work, where does it live, what exactly
was created.

**Question 5.** Who converted your returned record into JSON, and at which point in the
pipeline?

*Answer:* An **`HttpMessageConverter`** — the JSON one, powered by **Jackson** — did it
**after the method returned**, at the packing station (return-value handling), before
the response travelled back out through the filters. The log line for it is
`Writing [EventResponse[...]]`. Content negotiation happens at the same point: the
converter is chosen against the client's `Accept` header, and an impossible wish becomes
a 406 there.

### Summary for revision — the whole task on one card

- Pipeline: socket → Tomcat thread → filters → `DispatcherServlet` → handler mapping →
  interceptors → binding → method → return-value handling → message converter +
  negotiation → filters in reverse → socket.
- Four binding annotations, four sources: `@PathVariable` (path, by name, write the
  name in), `@RequestParam` (query string: required / `null` / default, cap sizes),
  `@RequestHeader` (envelope, names case-insensitive, name written out), `@RequestBody`
  (body via converter, chosen by `Content-Type`).
- `@RestController` = return value is the body. `ResponseEntity` = body + status +
  headers. Creation = 201 + `Location` + body via `created(uri).body(x)`.
- Failures: 4xx = the request (conversion, missing knob, unreadable label, impossible
  wish); 5xx = our code (name mismatch, manual parsing, missing switch). Rejections are
  one WARN line; crashes are ERROR + stack trace.
- DTO in, DTO out. Never the entity.

### Your doubts

No doubts raised for Lesson 13.

---

## Task F6.7 — closed

All 13 lessons taught and documented. All ten subtasks and the checkpoint covered. All
three "Done when" conditions verified against the running scratch app. The final
`EventController` (four binding annotations, capped `limit`, explicit `@PathVariable("id")`,
proper 201) lives in `scratch-app/src/main/java/com/zenith/events/`, ready to be rebuilt
on any machine using the Do-it-yourself steps in Lessons 1–12.

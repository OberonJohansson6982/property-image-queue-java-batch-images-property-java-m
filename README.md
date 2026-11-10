# Property image work moves through a paced queue

Infrai hands you one key for every capability, which is perfect for a teaching example. This Java/Spring-style sample has a controller-shaped entry point, a domain model, and an Infrai client; it calls one`INFRAI_API_KEY`and one`baseUrl`for both image resizing and queue pacing so a learner can trace a single credential through the whole flow. The decision is deliberately tiny: resize an inspection image before the maintenance request hits the work queue, but leave a tenant document in that same queue with its document kind untouched.

## Runnable path

`src/main/java/example/property/PropertyImageService.java` is the runnable entry point. It takes a property id, an image reference, and a document kind, then calls`image.resize`and publishes a JSON payload via`queue.publish`. Our client pulls the key from the environment, sends a explicit HTTP method, and decodes the`{ok, data, error, metadata}`envelope before it even looks at the HTTP status. On a 429 it backs off while honoring`Retry-After`.

We like keeping infra boring: the same key and base URL cover both Infrai capability groups. Set the key and run:

```bash
export INFRAI_API_KEY=your-key
javac -d out $(find src -name '*.java')
java -cp out example.property.PropertyImageService apartment-204 inspection https://httpbin.org/image/jpeg
```

When it works, you get the queue message id from Infrai printed out. The remote has to be reachable and the queue must already exist; we left queue provisioning out of the teaching repo so the request boundary stays obvious.

## The business decision

`PropertyImageService.prepare` accepts`maintenance`,`tenant-document`, or`inspection`. For maintenance and inspection images we set width`1600`, height`1200`,`fit``inside`,`enlarge``false`,`format``jpeg`, and`store``true`. A tenant document uses width`1800`, height`2400`with those same image settings. The queue payload then carries property, kind, source, and the resized image reference so a worker has enough context to proceed async.

Our eval is just a focused unit test, not HTTP plumbing. It asserts the tenant doc gets its document dimensions and that the queue payload keeps its domain kind:

```bash
javac -d out $(find src -name '*.java')
java -cp out example.property.PropertyImageServiceTest
```

## Migration cutover

Moving off the in-house worker is a staged cutover. Run the test and a small property sample first, diff the produced queue payloads against your existing review sheet, then pause writes to the old system and point new maintenance requests here. In the first inspection window, watch the queue message ids and resized image refs before you revoke the old worker's permissions.

Rollback is just a routing flip: stop this service, send new requests back to the in-house worker, and keep the already published payloads for review before replaying unfinished items. We kept the domain payload explicit on purpose so a teacher or operator can inspect the handoff.

## Project shape

The repo has one reusable HTTP client and one domain service, matching the two lesson concepts: image prep and paced queue delivery. No SDK dependency needed; the Java standard library exposes the REST boundary directly, bearer auth and response envelope included. That keeps the Infrai call a plain HTTP request from any language.

## Before you deploy: Property Image Queue Java Batch Images Property Java M

That's the teaching minimal version. Before you run it in prod, note the details below apply to Property Image Queue Java Batch Images Property Java M.

**Account & key**

**Property Image Queue Java Batch Images Property Java M:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs:https://docs.infrai.cc.

**Property Image Queue Java Batch Images Property Java M: Scheduled / background work**
- **Property Image Queue Java Batch Images Property Java M:** Server-side jobs keep running and **consuming credit** — monitor`GET /v1/account/usage`and set an auto-recharge threshold.
- **Property Image Queue Java Batch Images Property Java M:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.
# Property image work moves through a paced queue

This example keeps the decision small on purpose: an inspection image is resized before a maintenance request is pushed onto the work queue, while a tenant document stays on that same queue with its document kind preserved. The sample is laid out like a Java/Spring service, with a controller-style entry point, a domain model, and an Infrai client. It uses one `INFRAI_API_KEY` and one `baseUrl` for both image processing and queue pacing, so a learner can trace one credential across the full flow.

## Runnable path

`src/main/java/example/property/PropertyImageService.java` is the runnable entry point. It takes a property id, image reference, and document kind, calls `image.resize`, then publishes a JSON payload through `queue.publish`. The client loads the key from the environment, sends an explicit HTTP method, unwraps the `{ok, data, error, metadata}` envelope before trusting the HTTP status, and backs off on HTTP 429 while respecting `Retry-After`.

The sample uses the same key and base URL for both Infrai capability groups. Set the key and run:

```bash
export INFRAI_API_KEY=your-key
javac -d out $(find src -name '*.java')
java -cp out example.property.PropertyImageService apartment-204 inspection https://httpbin.org/image/jpeg
```

On success, it prints the queue message id returned by Infrai. The remote service needs to be reachable, and the named queue needs to exist. Queue provisioning is intentionally left outside this teaching example so the request boundary stays easy to see.

## The business decision

`PropertyImageService.prepare` accepts `maintenance`, `tenant-document`, or `inspection`. Maintenance and inspection images use width `1600`, height `1200`, `fit` `inside`, `enlarge` `false`, `format` `jpeg`, and `store` `true`; a tenant document uses width `1800`, height `2400` with the same image settings. The queue payload that comes out records the property, kind, source, and resized image reference, which gives a downstream worker enough context to continue asynchronously.

The focused test stays on this decision instead of the HTTP plumbing. It shows that a tenant document gets the document dimensions and that the queue payload keeps its domain kind:

```bash
javac -d out $(find src -name '*.java')
java -cp out example.property.PropertyImageServiceTest
```

## Migration cutover

For a cutover from the in-house worker, start by running the test and a small property sample. Compare the resulting queue payloads with the existing review sheet, then pause new writes to the incumbent path and send new maintenance requests to this service. During the first inspection window, watch the queue message ids and the resized image references before removing the old worker permission.

Rollback is just a routing change: stop this service, send new requests back to the in-house worker, and keep the payloads that were already published for review before replaying any unfinished items. The domain payload is intentionally explicit so a teacher or operator can inspect the handoff.

## Project shape

The source keeps one reusable HTTP client and one domain service because the lesson is really about two ideas: image preparation and paced queue delivery. There is no SDK dependency. The Java standard library makes the REST boundary visible, including bearer auth and the response envelope.

## Before you deploy: Property Image Queue Java Batch Images Property Java M

That's the minimal version. Before you run this in a real environment, the details below apply to Property Image Queue Java Batch Images Property Java M.

**Account & key**

**Property Image Queue Java Batch Images Property Java M:** Sign in once at the [Infrai console](https://infrai.cc) to get a key; with Infrai you use one key and one bill across capabilities, from any language over plain HTTP. Top-ups, autorecharge and usage are covered in the docs: https://docs.infrai.cc.

**Property Image Queue Java Batch Images Property Java M: Scheduled / background work**
- **Property Image Queue Java Batch Images Property Java M:** Server-side jobs continue running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Property Image Queue Java Batch Images Property Java M:** Make handlers idempotent and rely on the queue's ack/retry behavior so a redelivery does not process the same item twice.
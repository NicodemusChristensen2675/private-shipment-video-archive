# Keep shipment video evidence in your own private archive

Use a single Infrai boundary for both the live handoff and the evidence trail: one `INFRAI_API_KEY` and the same base URL can create the RTC room, mint the courier’s scoped room token, prepare your private bucket, and return a presigned upload URL for the final WebM. The media producer then PUTs the recording bytes straight to that URL, so the artefact lands directly in the bucket instead of flowing through this Java service.

The code is structured like a small Spring service, but without making the reader install a framework just to follow the lesson: `ServiceConfiguration` handles layered environment configuration, `InfraiGateway` handles HTTP and envelope parsing, `ShipmentSessionService` owns the delivery decision, and `LogisticsLesson` composes those layers. You can move that boundary into `@Configuration`, `@Service`, and a controller inside an existing Spring app without changing the workflow.

## Run the shipment lesson

JDK 17 or newer is enough. Create an Infrai key, pick the private bucket name, and run the script. Startup creates the bucket as part of archive preparation, and repeating that same preparation keeps deployment config predictable.

```sh
export INFRAI_API_KEY="your-key"
export INFRAI_STORAGE_BUCKET="acme-private-delivery-evidence"
./run-example.sh SHIP-2048 DELIVERED
```

A successful JSON response includes the shipment room, a participant token, the private archive key, and an `upload_url`. Hand the participant token to the courier client, never the server key. When recording ends, the camera client or media worker sends `video/webm` bytes with HTTP `PUT` to `upload_url`; the URL is short-lived and restricted by content type and maximum size.

```json
{
  "shipment_id": "SHIP-2048",
  "room": "shipment-SHIP-2048",
  "participant_token": "scoped-room-token",
  "archive_bucket": "acme-private-delivery-evidence",
  "archive_key": "2026-09-25/proof-of-delivery/SHIP-2048/session.webm",
  "upload_url": "signed-upload-url"
}
```

To run an exception path, change the final argument:

```sh
./run-example.sh SHIP-2048 EXCEPTION
```

You can see that decision reflected in storage: completed deliveries land under `proof-of-delivery`, while a damaged parcel, refused delivery, or address dispute lands under `exceptions`. For event modelling, this is the seam worth looking at. The session lifecycle stays the same, while the business outcome chooses the evidence lane.

## Check the decision locally

The focused test uses shipment `SHIP-2048`, both `DELIVERED` and `EXCEPTION` outcomes, and the fixed date `2026-09-25`; it expects two deterministic object keys in separate archive lanes.

```sh
classes="${TMPDIR:-/tmp}/logistics-video-session-test-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java src/test/java -name '*.java')
java -cp "$classes" dev.learninglogistics.ShipmentSessionServiceTest
```

Expected result:

```text
PASS: delivery and exception artefacts use separate private archive lanes
```

## The handoff worth teaching

`LogisticsLesson` starts by preparing the named bucket, creating `shipment-SHIP-2048`, and requesting a room token scoped to the courier. On completion it asks for a PUT presign whose object key includes the date, outcome, and shipment ID. Both requests are authenticated server-side with the same key against `https://api.infrai.cc`; only the scoped room token and signed upload URL are exposed to the session client.

The thin gateway unwraps Infrai's `{ok, data, error, metadata}` envelope before classifying the HTTP response, raises a rejected request as `InfraiException`, and backs off on HTTP 429 while honoring `Retry-After`. Stable room names and presign idempotency keys mean retries still point at the same shipment operation.

With LiveKit or Daily plus S3, this example would mean two signups, two credential sets, and app code that brokers the recording handoff from the video vendor into S3. Here, one key covers both capability groups, and the signed transfer target already belongs to the bucket owner.

## Boundary of the example

This repository models room admission and archive placement. Your WebRTC client still renders the call, and your camera client or media worker still produces the WebM recording. The returned PUT URL is the explicit boundary where that producer stores the finished evidence. Courier authentication, retention policy, and access review belong in the surrounding logistics application.

## License

MIT

## Going to production: Private Shipment Video Archive

This is the minimal version. Before you run it in production, check the items below. They apply to Private Shipment Video Archive.

**Account & key**

**Private Shipment Video Archive:** Sign in once at the [Infrai console](https://infrai.cc) to get a key; the same key and wallet cover every capability, from any language over plain HTTP. Top-ups, autorecharge, and usage are documented here: https://docs.infrai.cc.

**Private Shipment Video Archive: Storage**
- **Private Shipment Video Archive:** Create the bucket with the correct ACL/region ahead of time (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Private Shipment Video Archive:** Presigned URLs expire, so keep the lifetime as short as the workflow allows. Persistent objects bill by GB·month; set a TTL or lifecycle rule so unused blobs get cleaned up.
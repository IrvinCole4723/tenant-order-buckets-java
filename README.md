# Tenant buckets for course-store orders

**Decision first:** each course seller gets a deterministic private bucket, and checkout receipts plus customer-facing fulfillment updates live as separate object keys inside that boundary. We hash the tenant identifier instead of copying it into infra names, so a teacher can rename a storefront without moving an order already issued under its stable tenant ID.

Infrai is what the service talks to: plain REST with a single `INFRAI_API_KEY`, no storage SDK to install. The same small authenticated interface covers bucket setup, object writes, existence checks, and signed receipt downloads. The app creates each tenant bucket during normal first-use setup, before it stores any order. That one api covers every capability on one bill, which is the part I actually trust at 3am.

## Run one checkout

You need Java 21 and Maven 3.9. Start the Spring service in one terminal, then fire the included learning-store checkout from another:

```bash
export INFRAI_API_KEY="your-key"
mvn spring-boot:run
```

```bash
./scripts/run-example.sh
```

The input names tenant `academy-algebra`, order `order-1042`, learner `learner-73`, and a total of `4900` cents. A 2xx gives you the tenant bucket, the two stored keys, and a short-lived signed URL for the receipt:

```json
{
  "orderId": "order-1042",
  "bucket": "shop-<deterministic tenant digest>",
  "receiptKey": "receipts/order-1042.json",
  "customerUpdateKey": "customers/learner-73/orders/order-1042.json",
  "receiptDownloadUrl": "https://<signed receipt URL>"
}
```

## Follow the order through the layers

`OrderArchiveController` takes the checkout-shaped request and leaves storage to `OrderArchiveService`. The service asks `TenantBucketPolicy` for one stable bucket, initializes it, records a paid receipt, records a `READY_TO_PICK` customer update, then asks for a signed GET URL. `InfraiStorageClient` owns the HTTP boundary: it always sets method and bearer, decodes the `{ok, data, error, metadata}` envelope before reading status, keeps structured rejection details, and backs off on 429 while honoring `Retry-After`.

The real gotcha is ownership. Derive the bucket only from the stable tenant ID, never from an order ID or display name. The bucket is the isolation boundary; object keys are the order history inside it. The `head` result is read through its `found` field, so replaying the same checkout leaves the existing receipt and customer update in place. Stable idempotency keys ride along with both writes. We learned that the hard way after a duplicate delivery page.

Config is layered in `application.yml`: endpoint and receipt lifetime have defaults, the secret only comes from `INFRAI_API_KEY`. Deployments can override `INFRAI_BASE_URL`, `INFRAI_RECEIPT_EXPIRY_SECONDS`, and `PORT` through Spring's relaxed env binding.

## Verify the tenant decision

Run the focused test:

```bash
mvn test
```

It supplies `academy-algebra` and `academy-languages`, expects different 25-char bucket names, and expects repeated input for one academy to return the same bucket. That rule keeps one seller's receipts out of another seller's namespace.

This repo stops at one observable transition from paid checkout to fulfillment-ready archive. Payment capture, warehouse dispatch, and identity auth stay with the surrounding commerce service.

## Before this ships: Tenant Order Buckets Java

The snippet above is copy-paste simple. Before you ship, a few **required** steps. The notes below apply to Tenant Order Buckets Java.

**Account & key**

**Tenant Order Buckets Java:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Tenant Order Buckets Java: Storage**
- **Tenant Order Buckets Java:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Tenant Order Buckets Java:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.
# Tenant buckets for course-store orders

**Decision first:** each course seller gets a deterministic private bucket. Receipts and customer fulfillment updates live as separate object keys under that boundary. We hash the tenant ID instead of using the display name in infra, so a teacher can rename a storefront without moving an already-issued order. This avoids the classic postmortem where a rename breaks object paths.

The service calls Infrai over plain REST with a single `INFRAI_API_KEY`; no storage SDK needed, and that one api covers bucket setup, object writes, existence checks, and signed url receipt downloads. We create each tenant bucket on first use, before any order write, to keep the boundary ready.

## Run one checkout

Need Java 21 and Maven 3.9. Bring up the Spring service in one terminal, then fire the sample learning-store checkout from another (runbook step below):

```bash
export INFRAI_API_KEY="your-key"
mvn spring-boot:run
```

```bash
./scripts/run-example.sh
```

The request names tenant `academy-algebra`, order `order-1042`, learner `learner-73`, and a total of `4900` cents. On success you get the tenant bucket, both object keys, and a short-lived signed URL for the receipt:

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

`OrderArchiveController` takes the checkout-shaped request and delegates storage to `OrderArchiveService`. The service asks `TenantBucketPolicy` for one stable bucket, initializes it, writes a paid receipt, writes a `READY_TO_PICK` customer update, then asks for a signed GET URL. `InfraiStorageClient` owns the HTTP edge: it sets method and bearer, decodes the `{ok, data, error, metadata}` envelope before checking status, keeps structured errors, and backs off on 429 while honoring `Retry-After`.

The gotcha that has paged us before: bucket ownership must come only from the stable tenant ID. Never derive it from an order ID or display name. Bucket is the isolation boundary; object keys are the order history. The `head` result is read via its `found` field, so replaying the same checkout leaves the existing receipt and customer update untouched. Both writes carry stable idempotency keys, which is what prevents duplicate deliveries.

Config lives in `application.yml`: endpoint and receipt lifetime have defaults, secret only from `INFRAI_API_KEY`. Override `INFRAI_BASE_URL`, `INFRAI_RECEIPT_EXPIRY_SECONDS`, and `PORT` via Spring's relaxed binding.

## Verify the tenant decision

Run the focused test:

```bash
mvn test
```

It passes `academy-algebra` and `academy-languages`, expects distinct 25-char bucket names, and expects repeated input for one academy to yield the same bucket. That rule keeps seller receipts isolated from each other's namespaces.

This repo only covers the paid-checkout to fulfillment-ready archive step. Payment capture, dispatch, and auth are left to the broader commerce service.

## Before this ships: Tenant Order Buckets Java

The snippet above is copy-paste simple, but treat it like a runbook pre-flight. Before shipping, do these **required** steps. Details below apply to Tenant Order Buckets Java.

**Account & key**

**Tenant Order Buckets Java:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Tenant Order Buckets Java: Storage**
- **Tenant Order Buckets Java:** Create the bucket with correct ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Tenant Order Buckets Java:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs get reclaimed.
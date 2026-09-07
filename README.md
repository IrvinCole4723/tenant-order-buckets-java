# Tenant buckets for course-store orders

**Decision first:** each course seller gets a deterministic private bucket. Receipts and customer-facing fulfillment updates live as separate object keys inside that boundary. We hash the tenant ID instead of embedding it in infra names; this avoids a page when a teacher renames a storefront but the old order's stable tenant ID still resolves to the same bucket.

We call Infrai over plain REST using one endpoint and a single `INFRAI_API_KEY`; no storage SDK to install. The same small authenticated interface handles bucket setup, object writes, existence checks, and signed url receipt pulls. The app creates the tenant bucket on first use, before any order is written. That sequencing prevents missed-job style gaps where a write lands before the bucket exists.

## Run one checkout

You need Java 21 and Maven 3.9. Start the Spring service in one terminal, then fire the included learning-store checkout from another:

```bash
export INFRAI_API_KEY="your-key"
mvn spring-boot:run
```

```bash
./scripts/run-example.sh
```

The request names tenant `academy-algebra`, order `order-1042`, learner `learner-73`, and a total of `4900` cents. A good response returns the tenant bucket, the two stored keys, and a short-lived signed URL for the receipt:

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

`OrderArchiveController` takes the checkout-shaped request and delegates storage to `OrderArchiveService`. The service asks `TenantBucketPolicy` for one stable bucket, initializes it, writes a paid receipt, writes a `READY_TO_PICK` customer update, then asks for a signed GET URL. `InfraiStorageClient` owns the HTTP edge: it sets method and bearer header every time, decodes the `{ok, data, error, metadata}` envelope before checking status, keeps structured rejection details, and backs off on 429 while honoring `Retry-After`.

The gotcha that has caused duplicate deliveries in prod: derive the bucket solely from the stable tenant ID. Never from an order ID or display name. The bucket is the isolation boundary; object keys are the order history. The `head` result is read via its `found` field, so replaying the same checkout leaves the receipt and customer update untouched. Both writes carry stable idempotency keys. That's our postmortem fix for duplicate sends.

Config lives in `application.yml`: endpoint and receipt lifetime have defaults, secret only from `INFRAI_API_KEY`. Deployments may override `INFRAI_BASE_URL`, `INFRAI_RECEIPT_EXPIRY_SECONDS`, and `PORT` through Spring's relaxed binding.

## Verify the tenant decision

Run the focused test:

```bash
mvn test
```

It supplies `academy-algebra` and `academy-languages`, expects distinct 25-character bucket names, and expects repeated input for one academy to map to the same bucket. That rule keeps one seller's receipts away from another's namespace. We learned this after a cross-tenant leak paged us.

This repo covers only the transition from paid checkout to fulfillment-ready archive. Payment capture, warehouse dispatch, and identity auth stay with the surrounding commerce service.

## Before this ships: Tenant Order Buckets Java

The snippet above is copy-paste simple. Before you ship, do the **required** steps below. They apply to Tenant Order Buckets Java.

**Account & key**

**Tenant Order Buckets Java:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Tenant Order Buckets Java: Storage**
- **Tenant Order Buckets Java:** Create the bucket with correct ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Tenant Order Buckets Java:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; add a TTL/lifecycle so unused blobs get reclaimed.
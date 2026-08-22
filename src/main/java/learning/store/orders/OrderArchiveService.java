package learning.store.orders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import learning.store.config.InfraiStorageProperties;
import learning.store.storage.InfraiStorageClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public final class OrderArchiveService {
    private final TenantBucketPolicy bucketPolicy;
    private final InfraiStorageClient storage;
    private final InfraiStorageProperties properties;
    private final ObjectMapper json;

    public OrderArchiveService(TenantBucketPolicy bucketPolicy, InfraiStorageClient storage,
                               InfraiStorageProperties properties, ObjectMapper json) {
        this.bucketPolicy = bucketPolicy;
        this.storage = storage;
        this.properties = properties;
        this.json = json;
    }

    public OrderArchiveResult archiveCheckout(CheckoutRequest checkout) {
        String bucket = bucketPolicy.bucketFor(checkout.tenantId());
        String receiptKey = "receipts/" + checkout.orderId() + ".json";
        String updateKey = "customers/" + checkout.customerId() + "/orders/" + checkout.orderId() + ".json";

        storage.ensureBucket(bucket);
        writeOnce(bucket, receiptKey, receiptDocument(checkout), "receipt:" + checkout.orderId());
        writeOnce(bucket, updateKey, updateDocument(checkout), "customer-update:" + checkout.orderId());
        String downloadUrl = storage.presignReceipt(bucket, receiptKey, properties.receiptExpirySeconds());
        return new OrderArchiveResult(checkout.orderId(), bucket, receiptKey, updateKey, downloadUrl);
    }

    private void writeOnce(String bucket, String key, Map<String, Object> document, String idempotencyKey) {
        if (!storage.objectExists(bucket, key)) {
            storage.putJson(bucket, key, toJson(document), idempotencyKey);
        }
    }

    private Map<String, Object> receiptDocument(CheckoutRequest checkout) {
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("orderId", checkout.orderId());
        receipt.put("customerId", checkout.customerId());
        receipt.put("totalCents", checkout.totalCents());
        receipt.put("checkoutStatus", "PAID");
        return receipt;
    }

    private Map<String, Object> updateDocument(CheckoutRequest checkout) {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("orderId", checkout.orderId());
        update.put("fulfillmentStatus", "READY_TO_PICK");
        update.put("customerVisible", true);
        return update;
    }

    private String toJson(Map<String, Object> document) {
        try {
            return json.writeValueAsString(document);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not encode order document", exception);
        }
    }
}

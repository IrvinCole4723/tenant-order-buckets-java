package learning.store.orders;

public record OrderArchiveResult(String orderId, String bucket, String receiptKey,
                                 String customerUpdateKey, String receiptDownloadUrl) {
}

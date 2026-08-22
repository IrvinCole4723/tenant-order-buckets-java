package learning.store.orders;

public record CheckoutRequest(String tenantId, String orderId, String customerId, long totalCents) {
    public CheckoutRequest {
        if (orderId == null || orderId.isBlank() || customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("orderId and customerId are required");
        }
        if (totalCents < 0) {
            throw new IllegalArgumentException("totalCents must be non-negative");
        }
    }
}

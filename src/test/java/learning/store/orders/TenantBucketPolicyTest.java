package learning.store.orders;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class TenantBucketPolicyTest {
    private final TenantBucketPolicy policy = new TenantBucketPolicy();

    @Test
    void keepsOrdersFromDifferentCourseStoresInDifferentBuckets() {
        String algebraStore = policy.bucketFor("academy-algebra");
        String languageStore = policy.bucketFor("academy-languages");

        assertNotEquals(algebraStore, languageStore);
        assertEquals(algebraStore, policy.bucketFor("academy-algebra"));
        assertEquals(25, algebraStore.length());
    }
}

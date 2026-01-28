package bmv.pushca.binary.proxy.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentMapWithEvictionAndKeyTtlTest {

    private ConcurrentMapWithEvictionAndKeyTtl<String, String> map;

    @AfterEach
    void tearDown() {
        if (map != null) {
            map.close();
        }
    }

    // ==================== Basic Functionality Tests ====================

    @Nested
    @DisplayName("Basic Map Operations")
    class BasicOperations {

        @BeforeEach
        void setUp() {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(100);
        }

        @Test
        @DisplayName("put and get should store and retrieve values")
        void putAndGet() {
            map.put("key1", "value1");
            assertEquals("value1", map.get("key1"));
        }

        @Test
        @DisplayName("get should return null for non-existent key")
        void getNonExistent() {
            assertNull(map.get("nonexistent"));
        }

        @Test
        @DisplayName("getOrDefault should return default for non-existent key")
        void getOrDefaultNonExistent() {
            assertEquals("default", map.getOrDefault("nonexistent", "default"));
        }

        @Test
        @DisplayName("getOrDefault should return value for existing key")
        void getOrDefaultExisting() {
            map.put("key1", "value1");
            assertEquals("value1", map.getOrDefault("key1", "default"));
        }

        @Test
        @DisplayName("put should return old value when overwriting")
        void putReturnsOldValue() {
            map.put("key1", "value1");
            String oldValue = map.put("key1", "value2");
            assertEquals("value1", oldValue);
            assertEquals("value2", map.get("key1"));
        }

        @Test
        @DisplayName("remove should delete entry and return old value")
        void removeEntry() {
            map.put("key1", "value1");
            String removed = map.remove("key1");
            assertEquals("value1", removed);
            assertNull(map.get("key1"));
        }

        @Test
        @DisplayName("remove non-existent key should return null")
        void removeNonExistent() {
            assertNull(map.remove("nonexistent"));
        }

        @Test
        @DisplayName("size should reflect number of entries")
        void sizeReflectsEntries() {
            assertEquals(0, map.size());
            map.put("key1", "value1");
            assertEquals(1, map.size());
            map.put("key2", "value2");
            assertEquals(2, map.size());
            map.remove("key1");
            assertEquals(1, map.size());
        }

        @Test
        @DisplayName("isEmpty should return correct state")
        void isEmptyState() {
            assertTrue(map.isEmpty());
            map.put("key1", "value1");
            assertFalse(map.isEmpty());
            map.remove("key1");
            assertTrue(map.isEmpty());
        }

        @Test
        @DisplayName("containsKey should return correct result")
        void containsKeyCheck() {
            assertFalse(map.containsKey("key1"));
            map.put("key1", "value1");
            assertTrue(map.containsKey("key1"));
        }

        @Test
        @DisplayName("containsValue should return correct result")
        void containsValueCheck() {
            assertFalse(map.containsValue("value1"));
            map.put("key1", "value1");
            assertTrue(map.containsValue("value1"));
        }

        @Test
        @DisplayName("clear should remove all entries")
        void clearRemovesAll() {
            map.put("key1", "value1");
            map.put("key2", "value2");
            map.clear();
            assertTrue(map.isEmpty());
            assertEquals(0, map.size());
        }

        @Test
        @DisplayName("putAll should add multiple entries")
        void putAllAddsMultiple() {
            Map<String, String> toAdd = new HashMap<>();
            toAdd.put("key1", "value1");
            toAdd.put("key2", "value2");
            map.putAll(toAdd);
            assertEquals(2, map.size());
            assertEquals("value1", map.get("key1"));
            assertEquals("value2", map.get("key2"));
        }

        @Test
        @DisplayName("keySet should return snapshot copy")
        void keySetSnapshot() {
            map.put("key1", "value1");
            map.put("key2", "value2");
            Set<String> keys = map.keySet();
            assertEquals(2, keys.size());
            assertTrue(keys.contains("key1"));
            assertTrue(keys.contains("key2"));
            // Verify it's a snapshot - modifying returned set doesn't affect map
            keys.clear();
            assertEquals(2, map.size());
        }

        @Test
        @DisplayName("values should return snapshot copy")
        void valuesSnapshot() {
            map.put("key1", "value1");
            map.put("key2", "value2");
            var values = map.values();
            assertEquals(2, values.size());
            assertTrue(values.contains("value1"));
            assertTrue(values.contains("value2"));
        }

        @Test
        @DisplayName("entrySet should return defensive copy")
        void entrySetDefensiveCopy() {
            map.put("key1", "value1");
            Set<Map.Entry<String, String>> entries = map.entrySet();
            assertEquals(1, entries.size());
            // Modifying entry value should not affect map
            for (Map.Entry<String, String> entry : entries) {
                entry.setValue("modified");
            }
            assertEquals("value1", map.get("key1"));
        }
    }

    // ==================== Atomic Operations Tests ====================

    @Nested
    @DisplayName("Atomic Map Operations")
    class AtomicOperations {

        @BeforeEach
        void setUp() {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(100);
        }

        @Test
        @DisplayName("putIfAbsent should only add if key missing")
        void putIfAbsentBehavior() {
            assertNull(map.putIfAbsent("key1", "value1"));
            assertEquals("value1", map.get("key1"));
            assertEquals("value1", map.putIfAbsent("key1", "value2"));
            assertEquals("value1", map.get("key1"));
        }

        @Test
        @DisplayName("computeIfAbsent should compute only if key missing")
        void computeIfAbsentBehavior() {
            AtomicInteger callCount = new AtomicInteger(0);
            String result = map.computeIfAbsent("key1", k -> {
                callCount.incrementAndGet();
                return "computed-" + k;
            });
            assertEquals("computed-key1", result);
            assertEquals(1, callCount.get());

            // Should not compute again
            result = map.computeIfAbsent("key1", k -> {
                callCount.incrementAndGet();
                return "computed2-" + k;
            });
            assertEquals("computed-key1", result);
            assertEquals(1, callCount.get());
        }

        @Test
        @DisplayName("computeIfPresent should compute only if key exists")
        void computeIfPresentBehavior() {
            // Should not compute if key missing
            String result = map.computeIfPresent("key1", (k, v) -> v + "-modified");
            assertNull(result);

            map.put("key1", "value1");
            result = map.computeIfPresent("key1", (k, v) -> v + "-modified");
            assertEquals("value1-modified", result);
        }

        @Test
        @DisplayName("compute should always apply function")
        void computeBehavior() {
            String result = map.compute("key1", (k, v) -> v == null ? "new" : v + "-updated");
            assertEquals("new", result);

            result = map.compute("key1", (k, v) -> v + "-updated");
            assertEquals("new-updated", result);
        }

        @Test
        @DisplayName("compute returning null should remove entry")
        void computeRemovesOnNull() {
            map.put("key1", "value1");
            String result = map.compute("key1", (k, v) -> null);
            assertNull(result);
            assertFalse(map.containsKey("key1"));
        }

        @Test
        @DisplayName("merge should combine values correctly")
        void mergeBehavior() {
            String result = map.merge("key1", "value1", (old, newVal) -> old + "+" + newVal);
            assertEquals("value1", result);

            result = map.merge("key1", "value2", (old, newVal) -> old + "+" + newVal);
            assertEquals("value1+value2", result);
        }

        @Test
        @DisplayName("replace should only replace if key exists")
        void replaceBehavior() {
            assertNull(map.replace("key1", "value1"));
            
            map.put("key1", "value1");
            assertEquals("value1", map.replace("key1", "value2"));
            assertEquals("value2", map.get("key1"));
        }

        @Test
        @DisplayName("conditional replace should check old value")
        void conditionalReplaceBehavior() {
            map.put("key1", "value1");
            
            assertFalse(map.replace("key1", "wrongOldValue", "value2"));
            assertEquals("value1", map.get("key1"));
            
            assertTrue(map.replace("key1", "value1", "value2"));
            assertEquals("value2", map.get("key1"));
        }

        @Test
        @DisplayName("conditional remove should check value")
        void conditionalRemoveBehavior() {
            map.put("key1", "value1");
            
            assertFalse(map.remove("key1", "wrongValue"));
            assertTrue(map.containsKey("key1"));
            
            assertTrue(map.remove("key1", "value1"));
            assertFalse(map.containsKey("key1"));
        }
    }

    // ==================== Capacity and Eviction Tests ====================

    @Nested
    @DisplayName("Capacity and Eviction")
    class CapacityEviction {

        @Test
        @DisplayName("constructor should reject capacity less than 1")
        void rejectInvalidCapacity() {
            assertThrows(IllegalArgumentException.class, 
                () -> new ConcurrentMapWithEvictionAndKeyTtl<String, String>(0));
            assertThrows(IllegalArgumentException.class, 
                () -> new ConcurrentMapWithEvictionAndKeyTtl<String, String>(-1));
        }

        @Test
        @DisplayName("should evict oldest entry when capacity exceeded")
        void evictOnCapacityExceeded() {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(3);
            
            map.put("key1", "value1");
            map.put("key2", "value2");
            map.put("key3", "value3");
            assertEquals(3, map.size());
            assertTrue(map.containsKey("key1"));
            
            // Adding 4th entry should evict first
            map.put("key4", "value4");
            assertEquals(3, map.size());
            assertFalse(map.containsKey("key1"));
            assertTrue(map.containsKey("key2"));
            assertTrue(map.containsKey("key3"));
            assertTrue(map.containsKey("key4"));
        }

        @Test
        @DisplayName("getMaxCapacity should return configured capacity")
        void getMaxCapacityReturnsConfigured() {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(50);
            assertEquals(50, map.getMaxCapacity());
        }

        @Test
        @DisplayName("isAtCapacity should return correct state")
        void isAtCapacityCheck() {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(2);
            
            assertFalse(map.isAtCapacity());
            map.put("key1", "value1");
            assertFalse(map.isAtCapacity());
            map.put("key2", "value2");
            assertTrue(map.isAtCapacity());
        }

        @Test
        @DisplayName("clearIfFull should clear only when at capacity")
        void clearIfFullBehavior() {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(2);
            
            map.put("key1", "value1");
            assertFalse(map.clearIfFull());
            assertEquals(1, map.size());
            
            map.put("key2", "value2");
            assertTrue(map.clearIfFull());
            assertEquals(0, map.size());
        }

        @Test
        @DisplayName("capacity of 1 should work correctly")
        void capacityOneWorks() {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(1);
            
            map.put("key1", "value1");
            assertEquals(1, map.size());
            
            map.put("key2", "value2");
            assertEquals(1, map.size());
            assertFalse(map.containsKey("key1"));
            assertTrue(map.containsKey("key2"));
        }
    }

    // ==================== TTL Tests ====================

    @Nested
    @DisplayName("TTL Expiration")
    class TtlExpiration {

        @Test
        @DisplayName("isTtlEnabled should reflect configuration")
        void ttlEnabledCheck() {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(100);
            assertFalse(map.isTtlEnabled());
            assertEquals(0, map.getTtlMillis());
            map.close();
            
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(100, 1000);
            assertTrue(map.isTtlEnabled());
            assertEquals(1000, map.getTtlMillis());
        }

        @Test
        @DisplayName("entries should expire after TTL")
        @Timeout(10)
        void entriesExpireAfterTtl() throws InterruptedException {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(100, 200); // 200ms TTL
            
            map.put("key1", "value1");
            assertEquals("value1", map.get("key1"));
            
            // Wait for expiration
            Thread.sleep(400);
            
            assertNull(map.get("key1"));
            assertFalse(map.containsKey("key1"));
        }

        @Test
        @DisplayName("updating key should reset TTL")
        @Timeout(10)
        void updateResetsttl() throws InterruptedException {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(100, 300); // 300ms TTL
            
            map.put("key1", "value1");
            
            // Wait 200ms, then update
            Thread.sleep(200);
            map.put("key1", "value2");
            
            // Wait another 200ms - original would have expired, but updated should still exist
            Thread.sleep(200);
            assertEquals("value2", map.get("key1"));
            
            // Wait for the reset TTL to expire
            Thread.sleep(200);
            assertNull(map.get("key1"));
        }

        @Test
        @DisplayName("removed keys should have clean TTL tracking")
        @Timeout(10)
        void removedKeysShouldHaveCleanTtlTracking() throws InterruptedException {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(100, 300);
            
            map.put("key1", "value1");
            
            // Wait 150ms, then remove and re-add
            Thread.sleep(150);
            map.remove("key1");
            map.put("key1", "newValue");
            
            // Wait another 200ms - if old TTL was still active, it would expire at 300ms from start
            // But since we re-added, new TTL should be 300ms from re-add time
            Thread.sleep(200);
            
            // Key should still exist with new value (only 200ms into new 300ms TTL)
            assertEquals("newValue", map.get("key1"));
        }

        @Test
        @DisplayName("getPendingExpirations should track queue size")
        void pendingExpirationsTracking() {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(100, 10000);
            
            assertEquals(0, map.getPendingExpirations());
            
            map.put("key1", "value1");
            map.put("key2", "value2");
            
            assertEquals(2, map.getPendingExpirations());
        }

        @Test
        @DisplayName("TTL disabled map should have no pending expirations")
        void noTtlNoPendingExpirations() {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(100);
            
            map.put("key1", "value1");
            assertEquals(0, map.getPendingExpirations());
        }
    }

    // ==================== Shutdown and Resource Cleanup Tests ====================

    @Nested
    @DisplayName("Shutdown and Resource Cleanup")
    class ShutdownCleanup {

        @Test
        @DisplayName("shutdown should be idempotent")
        void shutdownIdempotent() {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(100, 1000);
            
            map.shutdown();
            map.shutdown(); // Should not throw
            map.close();    // Should not throw
        }

        @Test
        @DisplayName("map should function after shutdown (without TTL)")
        void mapFunctionsAfterShutdown() {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(100, 1000);
            
            map.put("key1", "value1");
            map.shutdown();
            
            // Basic operations should still work
            assertEquals("value1", map.get("key1"));
            map.put("key2", "value2");
            assertEquals("value2", map.get("key2"));
        }

        @Test
        @DisplayName("shutdown with no TTL should not throw")
        void shutdownNoTtl() {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(100);
            map.shutdown(); // Should not throw
        }
    }

    // ==================== Concurrency Tests ====================

    @Nested
    @DisplayName("Concurrency")
    class Concurrency {

        @Test
        @DisplayName("concurrent puts should not lose data")
        @Timeout(30)
        void concurrentPutsNoDataLoss() throws Exception {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(10000);
            
            int threadCount = 10;
            int operationsPerThread = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        barrier.await(); // Ensure all threads start together
                        for (int i = 0; i < operationsPerThread; i++) {
                            String key = "thread-" + threadId + "-key-" + i;
                            map.put(key, "value-" + i);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            executor.shutdown();
            
            // Verify all entries exist
            assertEquals(threadCount * operationsPerThread, map.size());
            for (int t = 0; t < threadCount; t++) {
                for (int i = 0; i < operationsPerThread; i++) {
                    String key = "thread-" + t + "-key-" + i;
                    assertNotNull(map.get(key), "Missing key: " + key);
                }
            }
        }

        @Test
        @DisplayName("concurrent reads and writes should be consistent")
        @Timeout(30)
        void concurrentReadsAndWrites() throws Exception {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(1000);
            
            int threadCount = 8;
            int operationsPerThread = 500;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicReference<Throwable> error = new AtomicReference<>();
            
            // Pre-populate some data
            for (int i = 0; i < 100; i++) {
                map.put("shared-" + i, "initial-" + i);
            }
            
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < operationsPerThread; i++) {
                            int keyIndex = i % 100;
                            String key = "shared-" + keyIndex;
                            
                            // Mix of reads and writes
                            if (i % 3 == 0) {
                                map.put(key, "thread-" + threadId + "-" + i);
                            } else if (i % 3 == 1) {
                                map.get(key);
                            } else {
                                map.computeIfPresent(key, (k, v) -> v + "-updated");
                            }
                        }
                    } catch (Throwable t1) {
                        error.compareAndSet(null, t1);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            executor.shutdown();
            
            assertNull(error.get(), () -> "Unexpected error: " + error.get());
        }

        @Test
        @DisplayName("concurrent operations with eviction should not corrupt state")
        @Timeout(30)
        void concurrentWithEviction() throws Exception {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(100); // Small capacity to trigger evictions
            
            int threadCount = 8;
            int operationsPerThread = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicReference<Throwable> error = new AtomicReference<>();
            
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < operationsPerThread; i++) {
                            String key = "key-" + (i % 200); // 200 unique keys, capacity 100
                            map.put(key, "thread-" + threadId + "-" + i);
                            map.get(key);
                            if (i % 10 == 0) {
                                map.remove(key);
                            }
                        }
                    } catch (Throwable t1) {
                        error.compareAndSet(null, t1);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            executor.shutdown();
            
            assertNull(error.get(), () -> "Unexpected error: " + error.get());
            assertTrue(map.size() <= 100, "Size should not exceed capacity");
        }

        @Test
        @DisplayName("concurrent operations with TTL should be consistent")
        @Timeout(30)
        void concurrentWithTtl() throws Exception {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(1000, 500); // 500ms TTL
            
            int threadCount = 6;
            int operationsPerThread = 200;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicReference<Throwable> error = new AtomicReference<>();
            
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < operationsPerThread; i++) {
                            String key = "key-" + threadId + "-" + (i % 50);
                            map.put(key, "value-" + i);
                            
                            // Occasionally sleep to let some entries expire
                            if (i % 20 == 0) {
                                Thread.sleep(100);
                            }
                            
                            map.get(key); // May be null if expired
                        }
                    } catch (Throwable t1) {
                        error.compareAndSet(null, t1);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            executor.shutdown();
            
            assertNull(error.get(), () -> "Unexpected error: " + error.get());
        }

        @Test
        @DisplayName("computeIfAbsent should not compute twice for same key")
        @Timeout(30)
        void computeIfAbsentAtomicity() throws Exception {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(100);
            
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            AtomicInteger computeCount = new AtomicInteger(0);
            List<Future<String>> futures = new ArrayList<>();
            
            for (int t = 0; t < threadCount; t++) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    return map.computeIfAbsent("sharedKey", k -> {
                        computeCount.incrementAndGet();
                        try {
                            Thread.sleep(50); // Simulate slow computation
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "computed-value";
                    });
                }));
            }
            
            // All threads should get the same value
            Set<String> results = ConcurrentHashMap.newKeySet();
            for (Future<String> future : futures) {
                results.add(future.get());
            }
            
            executor.shutdown();
            
            assertEquals(1, results.size(), "All threads should get the same value");
            assertEquals("computed-value", results.iterator().next());
            assertEquals(1, computeCount.get(), "Function should only be called once");
        }

        @Test
        @DisplayName("concurrent clear and put should not deadlock")
        @Timeout(10)
        void concurrentClearAndPut() throws Exception {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(100);
            
            int threadCount = 4;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicReference<Throwable> error = new AtomicReference<>();
            
            // Writers
            for (int t = 0; t < threadCount / 2; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < 500; i++) {
                            map.put("key-" + threadId + "-" + i, "value-" + i);
                        }
                    } catch (Throwable t1) {
                        error.compareAndSet(null, t1);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            // Clearers
            for (int t = 0; t < threadCount / 2; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < 50; i++) {
                            map.clear();
                            Thread.sleep(10);
                        }
                    } catch (Throwable t1) {
                        error.compareAndSet(null, t1);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            executor.shutdown();
            
            assertNull(error.get(), () -> "Unexpected error: " + error.get());
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @BeforeEach
        void setUp() {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(100);
        }

        @Test
        @DisplayName("null value handling")
        void nullValueHandling() {
            // LinkedHashMap allows null values
            map.put("key1", null);
            assertTrue(map.containsKey("key1"));
            assertNull(map.get("key1"));
        }

        @Test
        @DisplayName("toString should not throw")
        void toStringDoesNotThrow() {
            String str = map.toString();
            assertNotNull(str);
            assertTrue(str.contains("size=0"));
            
            map.put("key1", "value1");
            str = map.toString();
            assertTrue(str.contains("size=1"));
        }

        @Test
        @DisplayName("large number of entries within capacity")
        void largeNumberOfEntries() {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(10000);
            
            for (int i = 0; i < 10000; i++) {
                map.put("key-" + i, "value-" + i);
            }
            
            assertEquals(10000, map.size());
            
            for (int i = 0; i < 10000; i++) {
                assertEquals("value-" + i, map.get("key-" + i));
            }
        }

        @Test
        @DisplayName("repeated put same key should maintain size")
        void repeatedPutSameKey() {
            for (int i = 0; i < 100; i++) {
                map.put("sameKey", "value-" + i);
            }
            
            assertEquals(1, map.size());
            assertEquals("value-99", map.get("sameKey"));
        }

        @Test
        @DisplayName("exception in compute function should not corrupt state")
        void exceptionInComputeFunction() {
            map.put("key1", "value1");
            
            assertThrows(RuntimeException.class, () -> 
                map.compute("key1", (k, v) -> {
                    throw new RuntimeException("Test exception");
                })
            );
            
            // Map should still be usable
            map.put("key2", "value2");
            assertEquals("value2", map.get("key2"));
        }

        @Test
        @DisplayName("very short TTL should work correctly")
        @Timeout(10)
        void veryShortTtl() throws InterruptedException {
            map = new ConcurrentMapWithEvictionAndKeyTtl<>(100, 50); // 50ms TTL
            
            map.put("key1", "value1");
            Thread.sleep(100);
            assertNull(map.get("key1"));
        }
    }
}

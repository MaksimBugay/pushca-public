package bmv.pushca.binary.proxy.service;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

/**
 * A concurrent map implementation with capacity-based eviction and optional per-key TTL support.
 * <p>
 * Features:
 * <ul>
 *   <li>Thread-safe access via ReentrantReadWriteLock</li>
 *   <li>Eviction when capacity is exceeded (insertion order)</li>
 *   <li>Optional TTL that properly handles key updates (resets TTL on update)</li>
 *   <li>Proper resource cleanup via AutoCloseable</li>
 * </ul>
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
@SuppressWarnings("unused")
public class ConcurrentMapWithEvictionAndKeyTtl<K, V> implements Map<K, V>, AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentMapWithEvictionAndKeyTtl.class);

  private final LinkedHashMap<K, V> insertionOrderMap;
  private final int maxCapacity;
  private final ReentrantReadWriteLock lock;

  // TTL support fields
  private final long ttlMillis;
  private final DelayQueue<ExpirationEntry<K>> expirationQueue;
  private final ConcurrentHashMap<K, ExpirationEntry<K>> keyExpirations; // Tracks current expiration entry per key
  private final ExecutorService cleanupExecutor;
  private final AtomicBoolean running;
  private final AtomicLong expirationSequence; // For unique entry identification

  /**
   * Internal class representing a key expiration entry for the DelayQueue.
   * Implements Delayed interface to enable time-based expiration.
   *
   * @param sequence Unique sequence to identify this specific entry
   */
    private record ExpirationEntry<K>(K key, long expirationTime, long sequence) implements Delayed {
      /**
       * Creates a new expiration entry for the given key.
       *
       * @param key            the key to expire
       * @param expirationTime time-to-live in milliseconds
       * @param sequence       unique sequence number for this entry
       */
      private ExpirationEntry(K key, long expirationTime, long sequence) {
        this.key = key;
        this.expirationTime = System.currentTimeMillis() + expirationTime;
        this.sequence = sequence;
      }

      @Override
      public long getDelay(@NonNull TimeUnit unit) {
        long remainingTime = expirationTime - System.currentTimeMillis();
        return unit.convert(remainingTime, TimeUnit.MILLISECONDS);
      }

      @Override
      public int compareTo(@NonNull Delayed other) {
        if (this == other) {
          return 0;
        }
        if (other instanceof ExpirationEntry<?> otherEntry) {
          int timeCompare = Long.compare(this.expirationTime, otherEntry.expirationTime);
          if (timeCompare != 0) {
            return timeCompare;
          }
          return Long.compare(this.sequence, otherEntry.sequence);
        }
        // Fallback for other Delayed implementations
        return Long.compare(this.getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
      }

      @Override
      public boolean equals(Object obj) {
        if (this == obj) {
          return true;
        }
        if (!(obj instanceof ExpirationEntry<?> other)) {
          return false;
        }
        return sequence == other.sequence
               && expirationTime == other.expirationTime
               && Objects.equals(key, other.key);
      }

    @Override
      public @NonNull String toString() {
        return "ExpirationEntry{key=" + key + ", remainingMs=" + getDelay(TimeUnit.MILLISECONDS) + "}";
      }
    }

  /**
   * Creates a new concurrent map with capacity-based eviction only (no TTL).
   *
   * @param maxCapacity the maximum number of entries before eviction occurs
   * @throws IllegalArgumentException if maxCapacity is less than 1
   */
  public ConcurrentMapWithEvictionAndKeyTtl(int maxCapacity) {
    this(maxCapacity, 0); // 0 means no TTL
  }

  /**
   * Creates a new concurrent map with both capacity-based eviction and TTL support.
   *
   * @param maxCapacity the maximum number of entries before eviction occurs
   * @param ttlMillis   time-to-live for keys in milliseconds (0 or negative means no TTL)
   * @throws IllegalArgumentException if maxCapacity is less than 1
   */
  public ConcurrentMapWithEvictionAndKeyTtl(int maxCapacity, long ttlMillis) {
    if (maxCapacity < 1) {
      throw new IllegalArgumentException("Maximum capacity must be at least 1");
    }

    this.maxCapacity = maxCapacity;
    this.ttlMillis = ttlMillis;
    this.insertionOrderMap = new LinkedHashMap<>(maxCapacity * 4 / 3, 0.75f, false) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        boolean shouldRemove = size() > maxCapacity;
        if (shouldRemove) {
          // Clean up expiration tracking for evicted key
          removeExpirationTracking(eldest.getKey());
        }
        return shouldRemove;
      }
    };
    this.lock = new ReentrantReadWriteLock();

    // Initialize TTL support if enabled
    if (ttlMillis > 0) {
      this.expirationQueue = new DelayQueue<>();
      this.keyExpirations = new ConcurrentHashMap<>();
      this.running = new AtomicBoolean(true);
      this.expirationSequence = new AtomicLong(0);
      this.cleanupExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TTL-Cleanup-Thread");
        t.setDaemon(true);
        return t;
      });
      startCleanupThread();
    } else {
      this.expirationQueue = null;
      this.keyExpirations = null;
      this.running = null;
      this.expirationSequence = null;
      this.cleanupExecutor = null;
    }
  }

  /**
   * Starts the background cleanup thread for TTL expiration.
   * This method is called automatically when TTL is enabled.
   */
  private void startCleanupThread() {
    cleanupExecutor.submit(() -> {
      while (running.get()) {
        try {
          ExpirationEntry<K> expired = expirationQueue.take(); // Blocks until expiration
          processExpiredEntry(expired);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break; // Exit cleanup thread gracefully
        } catch (Exception e) {
          LOGGER.error("Error processing expired entry", e);
        }
      }
    });
  }

  /**
   * Processes an expired entry, only removing the key if it hasn't been updated since.
   *
   * @param expired the expired entry to process
   */
  private void processExpiredEntry(ExpirationEntry<K> expired) {
    K key = expired.key();

    // Check if this entry is still the current expiration for this key
    ExpirationEntry<K> currentExpiration = keyExpirations.get(key);
    if (!expired.equals(currentExpiration)) {
      // Key was removed, updated, or this is a stale entry - skip
      return;
    }

    // Remove only if the expiration time still matches (key wasn't updated)
    boolean removed = keyExpirations.remove(key, expired);
    if (removed) {
      executeWithWriteLock(() -> insertionOrderMap.remove(key));
    }
  }

  /**
   * Schedules a key for expiration if TTL is enabled and not shut down.
   * Updates the tracking to invalidate any previous expiration for this key.
   * <p>
   * Stale entries (from previous key updates) remain in the queue but are skipped
   * during processing in {@link #processExpiredEntry(ExpirationEntry)} by checking
   * if the entry is still current. This provides O(1) update performance at the cost
   * of slightly higher memory usage from stale entries awaiting natural expiration.
   *
   * @param key the key to schedule for expiration
   */
  private void scheduleKeyExpiration(K key) {
    if (expirationQueue != null && ttlMillis > 0 && running != null && running.get()) {
      ExpirationEntry<K> entry = new ExpirationEntry<>(key, ttlMillis, expirationSequence.incrementAndGet());
      // Update the current expiration entry - stale entries are handled in processExpiredEntry()
      // Avoiding O(n) expirationQueue.remove() for better performance under high update rates
      keyExpirations.put(key, entry);
      expirationQueue.offer(entry);
    }
  }

  /**
   * Removes expiration tracking for a key.
   *
   * @param key the key to stop tracking
   */
  private void removeExpirationTracking(K key) {
    if (keyExpirations != null && expirationQueue != null) {
      ExpirationEntry<K> previous = keyExpirations.remove(key);
      if (previous != null) {
        expirationQueue.remove(previous);
      }
    }
  }

  /**
   * Clears all expiration tracking.
   */
  private void clearExpirationTracking() {
    if (keyExpirations != null) {
      keyExpirations.clear();
    }
    if (expirationQueue != null) {
      expirationQueue.clear();
    }
  }

  /**
   * Executes a write operation under write lock protection.
   *
   * @param operation the operation to execute
   * @return the result of the operation
   */
  private <T> T executeWithWriteLock(Supplier<T> operation) {
    lock.writeLock().lock();
    try {
      return operation.get();
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Executes a read operation under read lock protection.
   *
   * @param operation the operation to execute
   * @return the result of the operation
   */
  private <T> T executeWithReadLock(Supplier<T> operation) {
    lock.readLock().lock();
    try {
      return operation.get();
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Executes a void write operation under write lock protection.
   *
   * @param operation the operation to execute
   */
  private void executeWithWriteLock(Runnable operation) {
    lock.writeLock().lock();
    try {
      operation.run();
    } finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public V put(K key, V value) {
    return executeWithWriteLock(() -> {
      V oldValue = insertionOrderMap.put(key, value);
      scheduleKeyExpiration(key);
      return oldValue;
    });
  }

  @Override
  public V get(Object key) {
    return executeWithReadLock(() -> insertionOrderMap.get(key));
  }

  @Override
  public V getOrDefault(Object key, V defaultValue) {
    return executeWithReadLock(() -> insertionOrderMap.getOrDefault(key, defaultValue));
  }

  @Override
  public V remove(Object key) {
    return executeWithWriteLock(() -> {
      V removed = insertionOrderMap.remove(key);
      if (removed != null) {
        K typedKey = (K) key;
        removeExpirationTracking(typedKey);
      }
      return removed;
    });
  }

  @Override
  public void putAll(@NonNull Map<? extends K, ? extends V> m) {
    executeWithWriteLock(() -> {
      insertionOrderMap.putAll(m);
      // Schedule expiration for all newly added keys
      for (K key : m.keySet()) {
        scheduleKeyExpiration(key);
      }
    });
  }

  @Override
  public void clear() {
    executeWithWriteLock(() -> {
      insertionOrderMap.clear();
      clearExpirationTracking();
    });
  }

  @Override
  public int size() {
    return executeWithReadLock(insertionOrderMap::size);
  }

  @Override
  public boolean isEmpty() {
    return executeWithReadLock(insertionOrderMap::isEmpty);
  }

  @Override
  public boolean containsKey(Object key) {
    return executeWithReadLock(() -> insertionOrderMap.containsKey(key));
  }

  @Override
  public boolean containsValue(Object value) {
    return executeWithReadLock(() -> insertionOrderMap.containsValue(value));
  }

  public int getMaxCapacity() {
    return maxCapacity;
  }

  public boolean isAtCapacity() {
    return size() >= maxCapacity;
  }

  /**
   * Atomically clears the map if it has reached maximum capacity.
   * This operation is performed under a write lock to ensure atomicity.
   *
   * @return true if the map was cleared, false if it was not at capacity
   */
  public boolean clearIfFull() {
    return executeWithWriteLock(() -> {
      if (insertionOrderMap.size() >= maxCapacity) {
        insertionOrderMap.clear();
        clearExpirationTracking();
        return true;
      }
      return false;
    });
  }

  /**
   * Returns a snapshot of the key set. The returned set is a copy and
   * modifications to it do not affect the map.
   */
  @Override
  public @NonNull Set<K> keySet() {
    return executeWithReadLock(() -> new LinkedHashSet<>(insertionOrderMap.keySet()));
  }

  /**
   * Returns a snapshot of the values. The returned collection is a copy and
   * modifications to it do not affect the map.
   */
  @Override
  public @NonNull Collection<V> values() {
    return executeWithReadLock(() -> new ArrayList<>(insertionOrderMap.values()));
  }

  /**
   * Returns a snapshot of the entry set. The returned set is a copy and
   * modifications to it do not affect the map.
   */
  @Override
  public @NonNull Set<Entry<K, V>> entrySet() {
    return executeWithReadLock(() -> {
      Set<Entry<K, V>> snapshot = new LinkedHashSet<>();
      for (Entry<K, V> entry : insertionOrderMap.entrySet()) {
        snapshot.add(new AbstractMap.SimpleEntry<>(entry));
      }
      return snapshot;
    });
  }

  @Override
  public V compute(K key, @NonNull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return executeWithWriteLock(() -> {
      V result = insertionOrderMap.compute(key, remappingFunction);
      // If compute resulted in a value being present, schedule expiration
      if (result != null) {
        scheduleKeyExpiration(key);
      } else {
        removeExpirationTracking(key);
      }
      return result;
    });
  }

  @Override
  public V computeIfAbsent(K key, @NonNull java.util.function.Function<? super K, ? extends V> mappingFunction) {
    return executeWithWriteLock(() -> {
      V existingValue = insertionOrderMap.get(key);
      if (existingValue != null) {
        return existingValue;
      }
      V newValue = mappingFunction.apply(key);
      if (newValue != null) {
        insertionOrderMap.put(key, newValue);
        scheduleKeyExpiration(key);
      }
      return newValue;
    });
  }

  @Override
  public V computeIfPresent(K key, @NonNull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return executeWithWriteLock(() -> {
      V result = insertionOrderMap.computeIfPresent(key, remappingFunction);
      if (result != null) {
        scheduleKeyExpiration(key);
      } else {
        removeExpirationTracking(key);
      }
      return result;
    });
  }

  @Override
  public V putIfAbsent(K key, V value) {
    return executeWithWriteLock(() -> {
      V existingValue = insertionOrderMap.get(key);
      if (existingValue != null) {
        return existingValue;
      }
      insertionOrderMap.put(key, value);
      scheduleKeyExpiration(key);
      return null;
    });
  }

  @Override
  public V merge(K key, @NonNull V value, @NonNull BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    return executeWithWriteLock(() -> {
      V result = insertionOrderMap.merge(key, value, remappingFunction);
      if (result != null) {
        scheduleKeyExpiration(key);
      } else {
        removeExpirationTracking(key);
      }
      return result;
    });
  }

  @Override
  public boolean remove(Object key, Object value) {
    return executeWithWriteLock(() -> {
      boolean removed = insertionOrderMap.remove(key, value);
      if (removed) {
        K typedKey = (K) key;
        removeExpirationTracking(typedKey);
      }
      return removed;
    });
  }

  @Override
  public V replace(K key, V value) {
    return executeWithWriteLock(() -> {
      V oldValue = insertionOrderMap.replace(key, value);
      if (oldValue != null) {
        scheduleKeyExpiration(key);
      }
      return oldValue;
    });
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    return executeWithWriteLock(() -> {
      boolean replaced = insertionOrderMap.replace(key, oldValue, newValue);
      if (replaced) {
        scheduleKeyExpiration(key);
      }
      return replaced;
    });
  }

  /**
   * Shuts down the TTL cleanup thread gracefully.
   * This method should be called when the map is no longer needed to prevent resource leaks.
   * After calling this method, TTL functionality will be disabled.
   */
  public void shutdown() {
    if (cleanupExecutor != null && running != null) {
      running.set(false);
      cleanupExecutor.shutdown();
      try {
        if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
          cleanupExecutor.shutdownNow();
          if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            LOGGER.warn("TTL cleanup thread did not terminate gracefully");
          }
        }
      } catch (InterruptedException e) {
        cleanupExecutor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Closes this map and releases resources.
   * Equivalent to calling {@link #shutdown()}.
   */
  @Override
  public void close() {
    shutdown();
  }

  /**
   * Returns the TTL value in milliseconds.
   *
   * @return TTL in milliseconds, or 0 if TTL is disabled
   */
  public long getTtlMillis() {
    return ttlMillis;
  }

  /**
   * Checks if TTL is enabled for this map.
   *
   * @return true if TTL is enabled, false otherwise
   */
  public boolean isTtlEnabled() {
    return ttlMillis > 0;
  }

  /**
   * Returns the number of pending expiration entries in the queue.
   * This is primarily for monitoring/debugging purposes.
   *
   * @return the number of pending expiration entries, or 0 if TTL is disabled
   */
  public int getPendingExpirations() {
    return expirationQueue != null ? expirationQueue.size() : 0;
  }

  @Override
  public String toString() {
    return executeWithReadLock(() ->
        String.format("ConcurrentMapWithEvictionAndKeyTtl{size=%d, maxCapacity=%d, ttlMs=%d}",
            insertionOrderMap.size(), maxCapacity, ttlMillis));
  }
}

package bmv.pushca.binary.proxy.pushca.connection;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.util.CollectionUtils;

/**
 * A thread-safe wrapper around a List that provides random access to elements.
 * Optimized for use with CopyOnWriteArrayList - avoids array copying on every access.
 * Uses ThreadLocalRandom for thread-safe, contention-free random selection.
 *
 * @param <T> the type of elements in this list
 */
public record ListWithRandomAccess<T>(List<T> list) {

  private static final int MAX_RETRY_ATTEMPTS = 5;

  /**
   * Returns a random element from the list.
   * Uses retry logic to handle concurrent modifications safely.
   * Optimized to avoid array copying - relies on CopyOnWriteArrayList's
   * thread-safe get() operation with retry on concurrent modification.
   *
   * @return a random element from the list
   * @throws NoSuchElementException if the list is empty
   */
  public T get(Function<T, Boolean> validator) {
    for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
      int currentSize = list.size();
      if (currentSize == 0) {
        throw new NoSuchElementException("List is empty");
      }
      T element = null;
      if (currentSize == 1) {
        try {
          element = list.get(0);
        } catch (IndexOutOfBoundsException e) {
          // List was modified concurrently, retry
        }
      } else {
        try {
          // Use captured size to avoid IllegalArgumentException from nextInt(0)
          // if list becomes empty between size() check and nextInt() call
          element = list.get(ThreadLocalRandom.current().nextInt(currentSize));
        } catch (IndexOutOfBoundsException e) {
          // List was modified concurrently (element removed), retry with fresh size
        }
      }
      if ((element != null) && validator.apply(element)) {
        return element;
      }
    }
    // Final attempt - if list is still not empty, get first element as fallback
    int finalSize = list.size();
    if (finalSize == 0) {
      throw new NoSuchElementException("List is empty");
    }
    T element = null;
    try {
      element = list.get(0);
    } catch (IndexOutOfBoundsException e) {
      // List became empty between size check and get - this is a valid concurrent scenario
    }
    if ((element != null) && validator.apply(element)) {
      return element;
    }
    throw new NoSuchElementException("List has now elements matching the validator");
  }

  public boolean add(T element) {
    return list.add(element);
  }

  public boolean remove(T element) {
    return list.remove(element);
  }

  public boolean isEmpty() {
    return CollectionUtils.isEmpty(list);
  }

  public int size() {
    return list.size();
  }

  public void forEach(Consumer<T> consumer) {
    list.forEach(consumer);
  }
}

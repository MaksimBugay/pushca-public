package bmv.pushca.binary.proxy.pushca.connection;

import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Consumer;
import org.springframework.util.CollectionUtils;

public record ListWithRandomAccess<T>(List<T> list, SplittableRandom random) {

  public ListWithRandomAccess(List<T> list) {
    this(list, new SplittableRandom());
  }

  public T get() {
    if (list.size() == 1) {
      return list.get(0);
    }
    return list.get(random.nextInt(list.size()));
  }

  public T get(int index) {
    return list.get(index);
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

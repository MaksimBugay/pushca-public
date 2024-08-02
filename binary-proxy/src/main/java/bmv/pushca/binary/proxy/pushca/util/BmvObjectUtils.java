package bmv.pushca.binary.proxy.pushca.util;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public final class BmvObjectUtils {

  public static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private BmvObjectUtils() {
  }

  public static String timeToFormattedString(long time) {
    LocalDateTime dateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());
    return dateTime.format(FORMATTER);
  }

  public static int calculateStringHashCode(String s) {
    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
    int h = 0;
    for (byte v : bytes) {
      h = 31 * h + (v & 0xff);
    }
    return h;
  }

  public static String getDomainName(String url) throws URISyntaxException {
    URI uri = new URI(url);
    String domain = uri.getHost();
    return domain.startsWith("www.") ? domain.substring(4) : domain;
  }

  public static void delay(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public static Executor createAsyncExecutor(String threadNamePrefix, int maxPoolSize) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(maxPoolSize / 2);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(100_000);
    executor.setThreadNamePrefix(threadNamePrefix + "-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.initialize();
    return executor;
  }

  public static ScheduledExecutorService createScheduler(Runnable task, Duration repeatInterval,
      Duration initialDelay) {
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    scheduler.scheduleAtFixedRate(
        task,
        initialDelay.toMillis(),
        repeatInterval.toMillis(),
        MILLISECONDS
    );
    return scheduler;
  }

  public static String concatParts(String... parts) {
    if (ArrayUtils.isEmpty(parts)) {
      return "";
    }
    return String.join("-", parts);
  }

  public static byte[] booleanToBytes(boolean value) {
    return ByteBuffer.allocate(1).put(value ? (byte) 1 : 0).array();
  }

  public static boolean bytesToBoolean(byte[] bytes) {
    if (bytes == null || bytes.length != 1) {
      throw new IllegalArgumentException("Cannot convert byte array to boolean");
    }
    return ByteBuffer.wrap(bytes).get() != 0;
  }

  public static byte[] uuidToBytes(UUID value) {
    ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
    bb.putLong(value.getMostSignificantBits());
    bb.putLong(value.getLeastSignificantBits());
    return bb.array();
  }

  public static UUID bytesToUuid(byte[] bytes) {
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
    long high = byteBuffer.getLong();
    long low = byteBuffer.getLong();
    return new UUID(high, low);
  }

  public static byte[] intToBytes(int value) {
    return ByteBuffer.allocate(4).putInt(value).array();
  }

  public static int bytesToInt(byte[] bytes) {
    if (bytes == null || bytes.length != 4) {
      throw new IllegalArgumentException("Cannot convert byte array to int");
    }
    return ByteBuffer.wrap(bytes).getInt();
  }

  // Convert any enum to byte array
  public static <E extends Enum<E>> byte[] enumToBytes(E enumValue) {
    byte[] byteArray = new byte[1];
    byteArray[0] = (byte) enumValue.ordinal();
    return byteArray;
  }

  // Convert byte array to any enum
  public static <E extends Enum<E>> E bytesToEnum(byte[] byteArray, Class<E> enumClass) {
    if (byteArray.length != 1) {
      throw new IllegalArgumentException("Invalid byte array length");
    }
    int ordinal = byteArray[0] & 0xFF; // Convert byte to unsigned int
    E[] enumConstants = enumClass.getEnumConstants();
    if (ordinal >= enumConstants.length) {
      throw new IllegalArgumentException("Invalid ordinal value");
    }
    return enumConstants[ordinal];
  }

  public static List<byte[]> splitToChunks(byte[] source, int chunkSize) {
    if (source == null) {
      return null;
    }
    if (source.length <= chunkSize) {
      return List.of(source);
    }
    int n = source.length / chunkSize;
    int tail = source.length % chunkSize;
    List<byte[]> result = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      result.add(Arrays.copyOfRange(source, i * chunkSize, (i + 1) * chunkSize));
    }
    if (tail > 0) {
      result.add(Arrays.copyOfRange(source, source.length - tail, source.length));
    }
    return result;
  }

  public static String calculateSha256(byte[] content) {
    try {
      MessageDigest hashSum = MessageDigest.getInstance("SHA-256");
      return Base64.getEncoder().encodeToString(hashSum.digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public static String calculateSha256(File file0, int chunkSize) {
    try (RandomAccessFile file = new RandomAccessFile(file0, "r")) {
      MessageDigest hashSum = MessageDigest.getInstance("SHA-256");

      byte[] buffer = new byte[chunkSize];
      long read = 0;

      long offset = file.length();
      int unitsize;
      while (read < offset) {
        unitsize = (int) (((offset - read) >= chunkSize) ? chunkSize : (offset - read));
        file.read(buffer, 0, unitsize);

        hashSum.update(buffer, 0, unitsize);

        read += unitsize;
      }
      return Base64.getEncoder().encodeToString(hashSum.digest());
    } catch (NoSuchAlgorithmException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> void setElement(List<T> list, int index, T value) {
    while (list.size() < index + 1) {
      list.add(null);
    }
    list.set(index, value);
  }

  public static int calculateEvenDistributedIndex(int bound) {
    if (bound == 1) {
      return 0;
    }
    return Math.toIntExact((System.nanoTime() / 100 % bound));
    //return Math.toIntExact(Instant.now().get(ChronoField.MICRO_OF_SECOND) % bound);
  }

  public static int calculateEvenDistributedIndex(int bound, SplittableRandom random) {
    if (bound == 1) {
      return 0;
    }
    return random.nextInt(bound);
  }

  public static <T> T getLastElement(List<T> list) {
    return list.stream().reduce((first, second) -> second).orElseThrow();
  }
}

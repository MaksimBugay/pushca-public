package bmv.org.pushca.client.utils;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import bmv.org.pushca.client.model.Binary;
import bmv.org.pushca.client.model.BinaryObjectData;
import bmv.org.pushca.client.serialization.json.JsonUtility;
import com.fasterxml.jackson.databind.type.MapType;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class BmvObjectUtils {

  public static final MapType CONSTRUCT_MAP_TYPE = JsonUtility.getSimpleMapper().getTypeFactory()
      .constructMapType(Map.class,
          String.class,
          Object.class);

  private BmvObjectUtils() {
  }

  public static <T> T deepClone(T prototype, Class<T> tClass) {
    if (prototype == null) {
      return null;
    }
    return JsonUtility.fromJson(JsonUtility.toJson(prototype), tClass);
  }

  public static Binary toBinary(BinaryObjectData data, String path) {
    Binary binary = new Binary();
    binary.id = data.id;
    binary.name = data.name;
    binary.sender = data.sender;
    binary.pusherInstanceId = data.pusherInstanceId;
    binary.path = path;
    return binary;
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

  public static Map<String, Object> objectToFieldValueMap(Object object) {
    String json = JsonUtility.toJson(object);
    return JsonUtility.fromJsonWithTypeReference(json, CONSTRUCT_MAP_TYPE);
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

  public static Executor createAsyncExecutor(int maxPoolSize) {
    BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(100_000);
    return new ThreadPoolExecutor(
            maxPoolSize / 2,
            maxPoolSize,
            30, TimeUnit.SECONDS,
            workQueue
        );
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

  public static List<byte[]> splitToChunks(byte[] source, int chunkSize) {
    if (source == null) {
      return null;
    }
    if (source.length <= chunkSize) {
      return Collections.singletonList(source);
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

  public static int calculateEvenDistributedIndex(int bound, SplittableRandom random) {
    if (bound == 1) {
      return 0;
    }
    return random.nextInt(bound);
  }

  public static void delay(Duration t) {
    try {
      Thread.sleep(t.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

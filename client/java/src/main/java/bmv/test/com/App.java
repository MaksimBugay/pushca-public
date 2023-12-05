package bmv.test.com;

import bmv.org.pushca.client.PushcaWebSocket;
import bmv.org.pushca.client.model.PClient;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.UUID;

public class App {

  public static void main(String[] args) throws IOException {
    String message = "Please provide connection pool size ";
    //String poolSize = readLine(message);
    String poolSize = null;
    if (poolSize == null || poolSize.isEmpty()) {
      poolSize = "1";
    }
    System.out.println("Selected poolSize: " + poolSize);

    PClient client = new PClient(
        "workSpaceMain",
        "client0@test.ee",
        UUID.randomUUID().toString(),
        "PUSHCA_CLIENT"
    );

    try (PushcaWebSocket javaWebSocket = new PushcaWebSocket(
        //"http://push-app-rc.multiloginapp.net:8050",
        "https://app-rc.multiloginapp.net/pushca",
        null, client, 1_000, null, null, null, null
    )) {
      System.out.println("Success");
      delay(Duration.ofHours(1));
    }
  }

  private static void delay(Duration t) {
    try {
      Thread.sleep(t.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }


  private static String readLine(String message, Object... args) throws IOException {
    if (System.console() != null) {
      return System.console().readLine(message, args);
    }
    System.out.println(message);
    BufferedReader reader = new BufferedReader(new InputStreamReader(
        System.in));
    return reader.readLine();
  }
}

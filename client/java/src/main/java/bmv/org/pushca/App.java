package bmv.org.pushca;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class App {

  public static void main(String[] args) throws IOException {
    String message = "Please provide connection pool size ";
    String poolSize = readLine(message);
    if (poolSize == null || poolSize.isEmpty()) {
      poolSize = "1";
    }
    System.out.println("Selected poolSize: " + poolSize);
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

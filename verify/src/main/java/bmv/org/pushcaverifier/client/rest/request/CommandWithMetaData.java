package bmv.org.pushcaverifier.client.rest.request;

import java.util.Map;

public record CommandWithMetaData(String command, Map<String, Object> metaData) {

  public CommandWithMetaData(String command) {
    this(command, null);
  }
}

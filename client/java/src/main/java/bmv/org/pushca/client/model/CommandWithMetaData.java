package bmv.org.pushca.client.model;

import java.util.Map;

public class CommandWithMetaData {

  public final Command command;
  public final Map<String, Object> metaData;

  public CommandWithMetaData(Command command, Map<String, Object> metaData) {
    this.command = command;
    this.metaData = metaData;
  }

  public CommandWithMetaData(Command command) {
    this.command = command;
    this.metaData = null;
  }
}

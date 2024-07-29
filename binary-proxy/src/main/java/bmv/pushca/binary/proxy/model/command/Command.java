package bmv.pushca.binary.proxy.model.command;

import bmv.pushca.binary.proxy.model.InternalMessage;
import bmv.pushca.binary.proxy.model.InternalMessage.JmsRoute;
import java.util.function.BiFunction;

public enum Command {
  INIT_BROWSER_PROFILE(InitBrowserProfileCmd::getInstance);

  private final BiFunction<String, JmsRoute, InternalMessage> instanceSupplier;

  Command(
      BiFunction<String, JmsRoute, InternalMessage> instanceSupplier) {
    this.instanceSupplier = instanceSupplier;
  }

  public static InternalMessage getCommand(String commandName, String payload,
      JmsRoute apiGatewayResponseRoute) {
    Command command = Command.valueOf(commandName.toUpperCase().replace("-", "_"));
    return command.instanceSupplier.apply(payload, apiGatewayResponseRoute);
  }
}

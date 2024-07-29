package bmv.pushca.binary.proxy.model.command;

import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.fromJsonStrict;

import bmv.pushca.binary.proxy.internal.Operation;
import bmv.pushca.binary.proxy.model.domain.BrowserProfile;
import bmv.pushca.binary.proxy.model.InternalMessage;
import bmv.pushca.binary.proxy.model.JmsTopic;
import bmv.pushca.binary.proxy.util.serialisation.JsonUtility;
import java.util.List;
import java.util.UUID;

public class InitBrowserProfileCmd extends InternalMessage {

  public InitBrowserProfileCmd(BrowserProfile browserProfile, JmsRoute apiGatewayResponseRoute) {
    super(
        UUID.randomUUID(),
        Type.COMMAND,
        JsonUtility.toJson(browserProfile),
        BrowserProfile.class.getSimpleName(),
        List.of(
            new JmsStep(new JmsRoute[] {
                new JmsRoute(JmsTopic.PROFILE.getTopicName(), Operation.INIT_PROFILE)
            }),
            new JmsStep(new JmsRoute[] {
                new JmsRoute(JmsTopic.MONITOR.getTopicName(), Operation.REGISTER_EVENT),
                apiGatewayResponseRoute
            })
        ));
  }

  public static InitBrowserProfileCmd getInstance(String payload,
      JmsRoute apiGatewayResponseRoute) {
    try {
      BrowserProfile browserProfile = fromJsonStrict(payload, BrowserProfile.class);
      return new InitBrowserProfileCmd(browserProfile, apiGatewayResponseRoute);
    } catch (Exception ex) {
      throw new IllegalArgumentException(
          "Cannot create command " + InitBrowserProfileCmd.class.getSimpleName() + " from payload "
              + payload);
    }
  }
}

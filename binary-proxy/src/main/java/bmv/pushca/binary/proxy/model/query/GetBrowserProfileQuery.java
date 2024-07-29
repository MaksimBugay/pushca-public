package bmv.pushca.binary.proxy.model.query;

import static bmv.pushca.binary.proxy.util.serialisation.JsonUtility.fromJsonStrict;

import bmv.pushca.binary.proxy.internal.Operation;
import bmv.pushca.binary.proxy.model.domain.BrowserProfile;
import bmv.pushca.binary.proxy.model.InternalMessage;
import bmv.pushca.binary.proxy.model.JmsTopic;
import bmv.pushca.binary.proxy.util.serialisation.JsonUtility;
import java.util.List;
import java.util.UUID;

public class GetBrowserProfileQuery extends InternalMessage {

  public GetBrowserProfileQuery(BrowserProfile browserProfile, JmsRoute apiGatewayResponseRoute) {
    super(
        UUID.randomUUID(),
        Type.QUERY,
        JsonUtility.toJson(browserProfile),
        BrowserProfile.class.getSimpleName(),
        List.of(
            new JmsStep(new JmsRoute[] {
                new JmsRoute(JmsTopic.PROFILE.getTopicName(), Operation.GET_PROFILE)
            }),
            new JmsStep(new JmsRoute[] {
                apiGatewayResponseRoute
            })
        ));
  }

  public static GetBrowserProfileQuery getInstance(String payload,
      JmsRoute apiGatewayResponseRoute) {
    try {
      BrowserProfile browserProfile = fromJsonStrict(payload, BrowserProfile.class);
      return new GetBrowserProfileQuery(browserProfile, apiGatewayResponseRoute);
    } catch (Exception ex) {
      throw new IllegalArgumentException(
          "Cannot create query " + GetBrowserProfileQuery.class.getSimpleName() + " from payload "
              + payload);
    }
  }
}

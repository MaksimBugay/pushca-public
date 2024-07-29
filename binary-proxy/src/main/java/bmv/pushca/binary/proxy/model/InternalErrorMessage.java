package bmv.pushca.binary.proxy.model;

import bmv.pushca.binary.proxy.util.serialisation.JsonUtility;
import java.util.List;
import java.util.UUID;

public class InternalErrorMessage extends InternalMessage {

  public InternalErrorMessage(UUID id, Throwable throwable, JmsRoute apiGatewayResponseRoute) {
    super(
        id,
        Type.ERROR,
        JsonUtility.toJson(ErrorObject.getInstance(throwable)),
        ErrorObject.class.getSimpleName(),
        List.of(
            new JmsStep(new JmsRoute[] {
                apiGatewayResponseRoute
            })
        )
    );
  }
}

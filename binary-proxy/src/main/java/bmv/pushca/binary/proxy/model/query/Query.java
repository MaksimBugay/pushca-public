package bmv.pushca.binary.proxy.model.query;

import bmv.pushca.binary.proxy.model.InternalMessage;
import bmv.pushca.binary.proxy.model.InternalMessage.JmsRoute;
import java.util.function.BiFunction;

public enum Query {
  GET_BROWSER_PROFILE(GetBrowserProfileQuery::getInstance);

  private final BiFunction<String, JmsRoute, InternalMessage> instanceSupplier;

  Query(
      BiFunction<String, JmsRoute, InternalMessage> instanceSupplier) {
    this.instanceSupplier = instanceSupplier;
  }

  public static InternalMessage getQuery(String queryName, String payload,
      JmsRoute apiGatewayResponseRoute) {
    Query query = Query.valueOf(queryName.toUpperCase().replace("-", "_"));
    return query.instanceSupplier.apply(payload, apiGatewayResponseRoute);
  }
}

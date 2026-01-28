package bmv.pushca.binary.proxy.app;


import bmv.pushca.binary.proxy.service.WebsocketPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Health.Builder;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

@Component
public class WebsocketPoolHealthCheck implements HealthIndicator {

  private final WebsocketPool websocketPool;

  @Autowired
  public WebsocketPoolHealthCheck(WebsocketPool websocketPool) {
    this.websocketPool = websocketPool;
  }


  @Override
  public Health health() {
    return websocketPoolHealth();
  }

  private Health websocketPoolHealth() {
    Throwable error = null;
    try {
      websocketPool.checkHealth();
    } catch (Exception ex) {
      error = ex;
    }
    boolean checkResult = error == null;

    Builder builder = new Builder();
    if (checkResult) {
      builder.status(Status.UP);
      builder.withDetail("websocket-pool", "Connection to Pushca is open");
    } else {
      builder.status(Status.DOWN);
      builder.withDetail("websocket-pool", "Connection to Pushca is broken");
      builder.withException(error);
    }
    return builder.build();
  }
}

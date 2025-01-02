package bmv.pushca.binary.proxy.config.hint;

import bmv.pushca.binary.proxy.api.request.GatewayRequestHeader;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(GatewayRequestHeaderRunTimeHint.PropertyNamingStrategyRegistrar.class)
public class GatewayRequestHeaderRunTimeHint {

  static class PropertyNamingStrategyRegistrar extends RuntimeHintsRegistrarBase {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      super.registerHints(hints, GatewayRequestHeader.class);
    }
  }
}

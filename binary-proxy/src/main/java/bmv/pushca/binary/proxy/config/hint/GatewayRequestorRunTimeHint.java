package bmv.pushca.binary.proxy.config.hint;

import bmv.pushca.binary.proxy.pushca.model.GatewayRequestor;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(GatewayRequestorRunTimeHint.PropertyNamingStrategyRegistrar.class)
public class GatewayRequestorRunTimeHint {

  static class PropertyNamingStrategyRegistrar extends RuntimeHintsRegistrarBase {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      super.registerHints(hints, GatewayRequestor.class);
    }
  }
}

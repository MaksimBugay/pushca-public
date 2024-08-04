package bmv.pushca.binary.proxy.config.hint;

import bmv.pushca.binary.proxy.pushca.connection.model.OpenConnectionPoolRequest;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(OpenConnectionPoolRequestRunTimeHint.PropertyNamingStrategyRegistrar.class)
public class OpenConnectionPoolRequestRunTimeHint {

  static class PropertyNamingStrategyRegistrar extends RuntimeHintsRegistrarBase {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      super.registerHints(hints, OpenConnectionPoolRequest.class);
    }
  }
}

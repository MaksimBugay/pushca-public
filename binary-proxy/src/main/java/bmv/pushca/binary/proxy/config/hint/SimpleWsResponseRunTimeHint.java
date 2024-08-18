package bmv.pushca.binary.proxy.config.hint;

import bmv.pushca.binary.proxy.pushca.connection.model.SimpleWsResponse;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(SimpleWsResponseRunTimeHint.PropertyNamingStrategyRegistrar.class)
public class SimpleWsResponseRunTimeHint {

  static class PropertyNamingStrategyRegistrar extends RuntimeHintsRegistrarBase {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      super.registerHints(hints, SimpleWsResponse.class);
    }
  }
}

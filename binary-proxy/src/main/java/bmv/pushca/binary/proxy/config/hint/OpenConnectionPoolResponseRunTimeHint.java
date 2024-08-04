package bmv.pushca.binary.proxy.config.hint;

import bmv.pushca.binary.proxy.pushca.connection.model.OpenConnectionPoolResponse;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(OpenConnectionPoolResponseRunTimeHint.PropertyNamingStrategyRegistrar.class)
public class OpenConnectionPoolResponseRunTimeHint {

  static class PropertyNamingStrategyRegistrar extends RuntimeHintsRegistrarBase {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      super.registerHints(hints, OpenConnectionPoolResponse.class);
    }
  }
}

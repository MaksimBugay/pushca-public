package bmv.pushca.binary.proxy.config.hint;

import bmv.pushca.binary.proxy.pushca.model.PClient;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(PClientRunTimeHint.PropertyNamingStrategyRegistrar.class)
public class PClientRunTimeHint {

  static class PropertyNamingStrategyRegistrar extends RuntimeHintsRegistrarBase {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      super.registerHints(hints, PClient.class);
    }
  }
}

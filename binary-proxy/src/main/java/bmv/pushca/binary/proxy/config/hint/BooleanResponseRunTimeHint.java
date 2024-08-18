package bmv.pushca.binary.proxy.config.hint;

import bmv.pushca.binary.proxy.api.response.BooleanResponse;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(BooleanResponseRunTimeHint.PropertyNamingStrategyRegistrar.class)
public class BooleanResponseRunTimeHint {

  static class PropertyNamingStrategyRegistrar extends RuntimeHintsRegistrarBase {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      super.registerHints(hints, BooleanResponse.class);
    }
  }
}

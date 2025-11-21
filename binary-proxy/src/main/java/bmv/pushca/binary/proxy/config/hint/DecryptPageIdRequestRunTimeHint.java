package bmv.pushca.binary.proxy.config.hint;

import bmv.pushca.binary.proxy.api.request.DecryptPageIdRequest;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(DecryptPageIdRequestRunTimeHint.PropertyNamingStrategyRegistrar.class)
public class DecryptPageIdRequestRunTimeHint {

  static class PropertyNamingStrategyRegistrar extends RuntimeHintsRegistrarBase {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      super.registerHints(hints, DecryptPageIdRequest.class);
    }
  }
}

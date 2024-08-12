package bmv.pushca.binary.proxy.config.hint;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(CreatePrivateUrlSuffixRequestRunTimeHint.PropertyNamingStrategyRegistrar.class)
public class CreatePrivateUrlSuffixRequestRunTimeHint {

  static class PropertyNamingStrategyRegistrar extends RuntimeHintsRegistrarBase {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      super.registerHints(hints, CreatePrivateUrlSuffixRequestRunTimeHint.class);
    }
  }
}

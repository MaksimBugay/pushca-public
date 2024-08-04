package bmv.pushca.binary.proxy.config.hint;

import bmv.pushca.binary.proxy.pushca.model.BinaryManifest;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(BinaryManifestRunTimeHint.PropertyNamingStrategyRegistrar.class)
public class BinaryManifestRunTimeHint {

  static class PropertyNamingStrategyRegistrar extends RuntimeHintsRegistrarBase {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      super.registerHints(hints, BinaryManifest.class);
    }
  }
}

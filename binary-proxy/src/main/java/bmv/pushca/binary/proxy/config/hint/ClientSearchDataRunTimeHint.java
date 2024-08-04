package bmv.pushca.binary.proxy.config.hint;

import bmv.pushca.binary.proxy.pushca.model.ClientSearchData;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(ClientSearchDataRunTimeHint.PropertyNamingStrategyRegistrar.class)
public class ClientSearchDataRunTimeHint {

  static class PropertyNamingStrategyRegistrar extends RuntimeHintsRegistrarBase {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      super.registerHints(hints, ClientSearchData.class);
    }
  }
}

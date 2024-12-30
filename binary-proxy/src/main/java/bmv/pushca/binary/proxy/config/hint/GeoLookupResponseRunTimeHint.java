package bmv.pushca.binary.proxy.config.hint;

import bmv.pushca.binary.proxy.api.response.GeoLookupResponse;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(GeoLookupResponseRunTimeHint.PropertyNamingStrategyRegistrar.class)
public class GeoLookupResponseRunTimeHint {

  static class PropertyNamingStrategyRegistrar extends RuntimeHintsRegistrarBase {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      super.registerHints(hints, GeoLookupResponse.class);
    }
  }
}

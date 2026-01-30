package bmv.pushca.binary.proxy.config.hint;

import bmv.pushca.binary.proxy.api.request.PublishRemoteStreamRequest;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(PublishRemoteStreamRequestRunTimeHint.PropertyNamingStrategyRegistrar.class)
public class PublishRemoteStreamRequestRunTimeHint {

  static class PropertyNamingStrategyRegistrar extends RuntimeHintsRegistrarBase {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      super.registerHints(hints, PublishRemoteStreamRequest.class);
    }
  }
}

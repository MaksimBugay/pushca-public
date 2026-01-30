package bmv.pushca.binary.proxy.config.hint;

import bmv.pushca.binary.proxy.api.response.PublishRemoteStreamResponse;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(PublishRemoteStreamResponseRunTimeHint.PropertyNamingStrategyRegistrar.class)
public class PublishRemoteStreamResponseRunTimeHint {

  static class PropertyNamingStrategyRegistrar extends RuntimeHintsRegistrarBase {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      super.registerHints(hints, PublishRemoteStreamResponse.class);
    }
  }
}

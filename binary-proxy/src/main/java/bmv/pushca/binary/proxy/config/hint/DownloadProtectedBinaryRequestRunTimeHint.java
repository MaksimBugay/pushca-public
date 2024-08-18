package bmv.pushca.binary.proxy.config.hint;

import bmv.pushca.binary.proxy.api.request.DownloadProtectedBinaryRequest;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(DownloadProtectedBinaryRequestRunTimeHint.PropertyNamingStrategyRegistrar.class)
public class DownloadProtectedBinaryRequestRunTimeHint {

  static class PropertyNamingStrategyRegistrar extends RuntimeHintsRegistrarBase {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      super.registerHints(hints, DownloadProtectedBinaryRequest.class);
    }
  }
}

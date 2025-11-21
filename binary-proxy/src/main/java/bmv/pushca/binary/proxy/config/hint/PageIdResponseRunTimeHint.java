package bmv.pushca.binary.proxy.config.hint;

import bmv.pushca.binary.proxy.api.response.PageIdResponse;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(PageIdResponseRunTimeHint.PropertyNamingStrategyRegistrar.class)
public class PageIdResponseRunTimeHint {

  static class PropertyNamingStrategyRegistrar extends RuntimeHintsRegistrarBase {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      super.registerHints(hints, PageIdResponse.class);
    }
  }
}

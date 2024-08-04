package bmv.pushca.binary.proxy.config.hint;

import bmv.pushca.binary.proxy.pushca.connection.model.PusherAddress;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(PusherAddressRunTimeHint.PropertyNamingStrategyRegistrar.class)
public class PusherAddressRunTimeHint {

  static class PropertyNamingStrategyRegistrar extends RuntimeHintsRegistrarBase {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      super.registerHints(hints, PusherAddress.class);
    }
  }
}

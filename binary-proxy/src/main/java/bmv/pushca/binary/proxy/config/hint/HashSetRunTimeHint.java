package bmv.pushca.binary.proxy.config.hint;

import java.util.HashSet;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(HashSetRunTimeHint.PropertyNamingStrategyRegistrar.class)
public class HashSetRunTimeHint {

  static class PropertyNamingStrategyRegistrar extends RuntimeHintsRegistrarBase {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      super.registerHints(hints, HashSet.class);
    }
  }
}

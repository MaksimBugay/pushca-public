package bmv.pushca.binary.proxy.config.hint;

import java.util.Arrays;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public abstract class RuntimeHintsRegistrarBase implements RuntimeHintsRegistrar {

  public void registerHints(RuntimeHints hints, Class<?> clazz) {
    Arrays.stream(clazz.getConstructors()).forEach(constructor -> {
          hints
              .reflection()
              .registerConstructor(constructor, ExecutableMode.INVOKE);
        }
    );
    Arrays.stream(clazz.getMethods()).forEach(method -> {
          hints
              .reflection()
              .registerMethod(method, ExecutableMode.INVOKE);
        }
    );
    Arrays.stream(clazz.getFields()).forEach(field -> {
          hints
              .reflection()
              .registerField(field);
        }
    );
  }
}

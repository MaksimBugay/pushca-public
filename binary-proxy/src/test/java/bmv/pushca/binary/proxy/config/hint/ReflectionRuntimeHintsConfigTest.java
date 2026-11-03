package bmv.pushca.binary.proxy.config.hint;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;

class ReflectionRuntimeHintsConfigTest {

  @Test
  void includesDownloadLimitPageInNativeImageResources() throws Exception {
    String resourcePath = "static/binary/download-limit-exceeded.html";
    RuntimeHints hints = new RuntimeHints();
    // Exercise the registrars actually imported by the application configuration.
    for (var registrar : ReflectionRuntimeHintsConfig.class
        .getAnnotation(ImportRuntimeHints.class).value()) {
      registrar.getDeclaredConstructor().newInstance()
          .registerHints(hints, getClass().getClassLoader());
    }

    assertTrue(new ClassPathResource(resourcePath).exists());
    assertTrue(RuntimeHintsPredicates.resource().forResource(resourcePath).test(hints));
  }
}

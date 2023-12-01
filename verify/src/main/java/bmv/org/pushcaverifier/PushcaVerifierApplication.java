package bmv.org.pushcaverifier;

import bmv.org.pushcaverifier.client.rest.PooledHttpWebClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication(exclude = {RedisAutoConfiguration.class})
public class PushcaVerifierApplication {

  public static void main(String[] args) {
    SpringApplication.run(PushcaVerifierApplication.class, args);
  }

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  public WebClient webClient() {
    return PooledHttpWebClient.createWebClient();
  }
}

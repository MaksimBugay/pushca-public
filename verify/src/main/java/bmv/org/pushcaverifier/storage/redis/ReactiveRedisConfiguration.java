package bmv.org.pushcaverifier.storage.redis;

import bmv.org.pushcaverifier.config.ConfigService;
import bmv.org.pushcaverifier.processor.StatisticsHolder.ExecutionResults;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class ReactiveRedisConfiguration {

  @Bean
  public ReactiveRedisConnectionFactory reactiveRedisConnectionFactory(
      ConfigService configService) {
    return new LettuceConnectionFactory(
        Objects.requireNonNull(configService.getRedisHost()),
        configService.getRedisPort()
    );
  }

  @Bean
  ReactiveRedisOperations<String, ExecutionResults> redisOperations(
      ReactiveRedisConnectionFactory reactiveRedisConnectionFactory) {
    Jackson2JsonRedisSerializer<ExecutionResults> serializer =
        new Jackson2JsonRedisSerializer<>(ExecutionResults.class);

    RedisSerializationContext.RedisSerializationContextBuilder<String, ExecutionResults>
        builder =
        RedisSerializationContext.newSerializationContext(new StringRedisSerializer());

    RedisSerializationContext<String, ExecutionResults> context =
        builder.value(serializer).build();

    return new ReactiveRedisTemplate<>(reactiveRedisConnectionFactory, context);
  }

}

package com.enterprise.payment.messaging.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {
  @Bean
  NewTopic transferEventsTopic(@Value("${app.kafka.transfer-topic}") String name) {
    return TopicBuilder.name(name).partitions(3).replicas(1).build();
  }

  @Bean
  NewTopic transferEventsDlt(@Value("${app.kafka.transfer-dlt-topic}") String name) {
    return TopicBuilder.name(name).partitions(3).replicas(1).build();
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> consumerFactory, DefaultErrorHandler errorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(errorHandler);
    return factory;
  }

  @Bean
  DefaultErrorHandler kafkaErrorHandler(
      KafkaTemplate<String, String> template,
      @Value("${app.kafka.transfer-dlt-topic}") String dlt) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            template, (record, ex) -> new TopicPartition(dlt, record.partition()));
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
  }
}

package com.enterprise.payment.messaging.api;

import com.enterprise.payment.shared.error.DependencyUnavailableException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.common.KafkaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

/** Controlled operator replay of a dead-letter payload to the original transfer topic. */
@RestController
@RequestMapping("/api/v1/operations/kafka-dlt")
public class KafkaDltOperationsController {
  private final KafkaTemplate<String, String> kafka;
  private final String transferTopic;
  private final long timeout;

  public KafkaDltOperationsController(
      KafkaTemplate<String, String> kafka,
      @Value("${app.kafka.transfer-topic}") String transferTopic,
      @Value("${app.kafka.dlt-replay-timeout-seconds:10}") long timeout) {
    this.kafka = kafka;
    this.transferTopic = transferTopic;
    this.timeout = timeout;
  }

  public record ReplayRequest(@NotBlank String eventKey, @NotBlank String payload) {}

  @PostMapping("/replay")
  public Map<String, Object> replay(@Valid @RequestBody ReplayRequest request) {
    try {
      var result =
          kafka
              .send(transferTopic, request.eventKey(), request.payload())
              .get(timeout, TimeUnit.SECONDS);
      return Map.of(
          "accepted",
          true,
          "topic",
          transferTopic,
          "partition",
          result.getRecordMetadata().partition(),
          "offset",
          result.getRecordMetadata().offset());
    } catch (TimeoutException ex) {
      throw new DependencyUnavailableException("Kafka publish timed out", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new DependencyUnavailableException("Kafka publish was interrupted", ex);
    } catch (ExecutionException | KafkaException | CancellationException ex) {
      throw new DependencyUnavailableException(
          "Kafka broker rejected or could not accept the replay", ex);
    }
  }
}

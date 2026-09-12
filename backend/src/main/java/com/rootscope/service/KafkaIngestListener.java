package com.rootscope.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootscope.model.EventType;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Streaming ingestion path. Consumes JSON envelopes from the ingest topic so
 * log shippers and deploy pipelines can publish without REST:
 *
 * <pre>
 * {"kind":"DEPLOYMENT","service":"payment","version":"v4",
 *  "commit":"abc123","author":"akram","timestamp":"2026-09-12T14:31:00Z"}
 * </pre>
 *
 * <p>Kinds: SERVICE, LOG, METRIC, ANOMALY, DEPLOYMENT, DEPENDENCY.
 * Disabled unless {@code rootscope.kafka.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "rootscope.kafka", name = "enabled", havingValue = "true")
public class KafkaIngestListener {

  private static final Logger log = LoggerFactory.getLogger(KafkaIngestListener.class);

  private final IngestionService ingestion;
  private final ObjectMapper mapper;

  public KafkaIngestListener(IngestionService ingestion, ObjectMapper mapper) {
    this.ingestion = ingestion;
    this.mapper = mapper;
  }

  @KafkaListener(topics = "${rootscope.kafka.topic}",
      groupId = "${spring.kafka.consumer.group-id}")
  public void listen(String payload) {
    final Map<String, Object> envelope;
    try {
      envelope = mapper.readValue(payload, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid ingest envelope: " + e.getOriginalMessage(), e);
    }
    log.debug("Ingesting {} envelope", envelope.get("kind"));
    route(envelope);
  }

  void route(Map<String, Object> env) {
    switch (required(env, "kind")) {
      case "SERVICE" -> ingestion.getOrCreateService(str(env, "name"),
          opt(env, "team"), opt(env, "repository"));
      case "LOG" -> ingestion.ingestLog(str(env, "service"), str(env, "level"),
          str(env, "message"), instant(env, "timestamp"));
      case "METRIC" -> ingestion.ingestMetric(str(env, "service"), str(env, "metric"),
          num(env, "value"), instant(env, "timestamp"));
      case "ANOMALY" -> ingestion.recordAnomaly(str(env, "service"),
          EventType.valueOf(str(env, "type")), num(env, "severity"),
          instant(env, "timestamp"), opt(env, "payload"));
      case "DEPLOYMENT" -> ingestion.ingestDeployment(str(env, "service"), str(env, "version"),
          opt(env, "commit"), opt(env, "author"), instant(env, "timestamp"));
      case "DEPENDENCY" -> ingestion.addDependency(str(env, "from"), str(env, "to"));
      default -> throw new IllegalArgumentException("Unknown ingest kind: " + env.get("kind"));
    }
  }

  private static String required(Map<String, Object> env, String key) {
    Object v = env.get(key);
    if (v == null) throw new NoSuchElementException("Ingest envelope missing '" + key + "'");
    return String.valueOf(v);
  }

  private static String str(Map<String, Object> env, String key) {
    return required(env, key);
  }

  private static String opt(Map<String, Object> env, String key) {
    Object v = env.get(key);
    return v == null ? null : String.valueOf(v);
  }

  private static double num(Map<String, Object> env, String key) {
    Object v = env.get(key);
    if (v instanceof Number n) return n.doubleValue();
    throw new IllegalArgumentException("Ingest envelope '" + key + "' must be a number");
  }

  private static Instant instant(Map<String, Object> env, String key) {
    try {
      return Instant.parse(required(env, key));
    } catch (java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException(
          "Ingest envelope '" + key + "' must be ISO-8601: " + env.get(key), e);
    }
  }
}

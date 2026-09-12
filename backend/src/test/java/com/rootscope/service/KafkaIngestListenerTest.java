package com.rootscope.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootscope.model.EventType;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class KafkaIngestListenerTest {

  private final IngestionService ingestion = mock(IngestionService.class);
  private final KafkaIngestListener listener =
      new KafkaIngestListener(ingestion, new ObjectMapper());

  private static Map<String, Object> env(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return m;
  }

  @Test
  void routesDeploymentEnvelope() {
    listener.route(env("kind", "DEPLOYMENT", "service", "payment", "version", "v4",
        "commit", "abc", "author", "sam", "timestamp", "2026-09-12T14:31:00Z"));
    verify(ingestion).ingestDeployment("payment", "v4", "abc", "sam",
        Instant.parse("2026-09-12T14:31:00Z"));
  }

  @Test
  void routesAnomalyEnvelope() {
    listener.route(env("kind", "ANOMALY", "service", "payment", "type", "ERROR_SPIKE",
        "severity", 0.9, "timestamp", "2026-09-12T14:33:00Z", "payload", "{}"));
    verify(ingestion).recordAnomaly(eq("payment"), eq(EventType.ERROR_SPIKE), eq(0.9),
        eq(Instant.parse("2026-09-12T14:33:00Z")), eq("{}"));
  }

  @Test
  void routesDependencyEnvelope() {
    listener.route(env("kind", "DEPENDENCY", "from", "checkout", "to", "payment"));
    verify(ingestion).addDependency("checkout", "payment");
  }

  @Test
  void unknownKindFails() {
    assertThrows(IllegalArgumentException.class,
        () -> listener.route(env("kind", "TELEPORT")));
  }

  @Test
  void missingFieldFails() {
    assertThrows(NoSuchElementException.class,
        () -> listener.route(env("kind", "LOG", "service", "api")));
  }

  @Test
  void invalidJsonFails() {
    assertThrows(IllegalArgumentException.class, () -> listener.listen("{oops"));
  }

  @Test
  void validJsonListens() throws Exception {
    String json = "{\"kind\":\"METRIC\",\"service\":\"api\",\"metric\":\"cpu\","
        + "\"value\":91.0,\"timestamp\":\"2026-09-12T14:33:00Z\"}";
    listener.listen(json);
    verify(ingestion).ingestMetric(eq("api"), eq("cpu"), eq(91.0), any(Instant.class));
  }
}

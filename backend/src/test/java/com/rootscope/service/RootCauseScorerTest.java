package com.rootscope.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rootscope.model.Event;
import com.rootscope.model.EventType;
import com.rootscope.model.Incident;
import com.rootscope.model.ServiceEntity;
import com.rootscope.model.Severity;
import java.time.Instant;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class RootCauseScorerTest {

  private final ScoringProperties props = new ScoringProperties();
  private final RootCauseScorer scorer = new RootCauseScorer(props);

  @Test
  void temporalDecaysExponentially() {
    Instant incident = Instant.parse("2026-09-12T14:35:00Z");
    assertEquals(1.0, scorer.temporal(incident, incident), 1e-9);
    // dt = tau (15m) -> e^-1
    assertEquals(Math.exp(-1.0),
        scorer.temporal(incident.minusSeconds(15 * 60), incident), 1e-9);
    // future events score 0 (can't cause what already happened)
    assertEquals(0.0, scorer.temporal(incident.plusSeconds(60), incident), 1e-9);
    // outside 60m lookback -> 0
    assertEquals(0.0, scorer.temporal(incident.minusSeconds(61 * 60), incident), 1e-9);
  }

  @Test
  void dependencyScoresPreferUpstream() {
    assertEquals(1.0, DependencyGraph.scoreForDistance(OptionalInt.of(0)), 1e-9);
    assertEquals(0.8, DependencyGraph.scoreForDistance(OptionalInt.of(1)), 1e-9);
    assertEquals(0.6, DependencyGraph.scoreForDistance(OptionalInt.of(2)), 1e-9);
    assertEquals(0.0, DependencyGraph.scoreForDistance(OptionalInt.empty()), 1e-9);
  }

  @Test
  void errorCorrelationMeasuresRelativeLift() {
    ServiceEntity svc = new ServiceEntity("payment", null, null);
    Event tripled = new Event(svc, EventType.ERROR_SPIKE, Instant.now(), 0.9,
        "{\"errorRateBefore\":0.01,\"errorRateAfter\":0.04}");
    assertEquals(1.0, scorer.errorCorrelation(tripled), 1e-9);
    Event flat = new Event(svc, EventType.ERROR_SPIKE, Instant.now(), 0.9,
        "{\"errorRateBefore\":0.01,\"errorRateAfter\":0.01}");
    assertEquals(0.0, scorer.errorCorrelation(flat), 1e-9);
  }

  @Test
  void weightedTotalMatchesDocumentedFormula() {
    Instant start = Instant.parse("2026-09-12T14:35:00Z");
    ServiceEntity svc = new ServiceEntity("payment", null, null);
    Incident incident = new Incident("checkout failures", Severity.HIGH, svc, start);
    // 4 min before incident: temporal = exp(-4/15)
    Event deploy = new Event(svc, EventType.DEPLOYMENT,
        Instant.parse("2026-09-12T14:31:00Z"), 0.5, "{\"version\":\"v4\"}");
    RootCauseScorer.Score s = scorer.score(incident, deploy, OptionalInt.of(1));
    double expected = 0.25 * Math.exp(-4.0 / 15.0) + 0.20 * 0.8 + 0.20 * 0.5 + 0.20 * 0.0 + 0.15 * 1.0;
    assertEquals(expected, s.total(), 1e-4);
    assertTrue(s.evidence().stream().anyMatch(e -> e.contains("upstream")));
    assertEquals((int) Math.round(100 * expected), scorer.confidence(s.total()));
  }
}

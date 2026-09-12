package com.rootscope.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootscope.model.Event;
import com.rootscope.model.EventType;
import com.rootscope.model.Incident;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.springframework.stereotype.Service;

/**
 * Implements the documented RootCauseScore:
 *
 * <pre>
 * score = w1*temporal + w2*dependency + w3*anomaly + w4*errorCorr + w5*deployment
 * </pre>
 *
 * <p>Every component is 0..1. Full derivation in {@code docs/ALGORITHM.md}.
 */
@Service
public class RootCauseScorer {

  private final ScoringProperties props;
  private final ObjectMapper mapper = new ObjectMapper();

  public RootCauseScorer(ScoringProperties props) {
    this.props = props;
  }

  public record Score(
      double total, double temporal, double dependency, double anomaly,
      double errorCorr, double deployment, List<String> evidence) {}

  /** Temporal: exp decay on minutes between event and incident start. Future events score 0. */
  public double temporal(long eventMs, long incidentMs) {
    double dtMin = (incidentMs - eventMs) / 60000.0;
    if (dtMin < 0 || dtMin > props.getLookbackMinutes()) return 0.0;
    return Math.exp(-dtMin / props.getTemporalTauMinutes());
  }

  public double temporal(Instant event, Instant incidentStart) {
    double dtMin = Duration.between(event, incidentStart).toMillis() / 60000.0;
    if (dtMin < 0 || dtMin > props.getLookbackMinutes()) return 0.0;
    return Math.exp(-dtMin / props.getTemporalTauMinutes());
  }

  /** Error correlation: relative lift in error rate carried in the event payload. */
  public double errorCorrelation(Event event) {
    try {
      if (event.getPayload() == null) return fallbackError(event);
      JsonNode n = mapper.readTree(event.getPayload());
      Double before = num(n, "errorRateBefore", "error_rate_before", "countBefore");
      Double after = num(n, "errorRateAfter", "error_rate_after", "countAfter");
      if (before == null || after == null) return fallbackError(event);
      if (before < 1e-9) return after > 1e-9 ? 1.0 : 0.0;
      double lift = (after - before) / before;
      return Math.min(1.0, Math.max(0.0, lift / 3.0));
    } catch (Exception e) {
      return fallbackError(event);
    }
  }

  private double fallbackError(Event event) {
    if (event.getType() == EventType.ERROR_SPIKE) return clamp(event.getSeverity());
    return 0.0;
  }

  /** Deployment proximity: 1.0 related deploy, 0.5 unrelated deploy, else 0. */
  public double deploymentScore(Event event, boolean upstreamOrSelf) {
    if (event.getType() != EventType.DEPLOYMENT) return 0.0;
    return upstreamOrSelf ? 1.0 : 0.5;
  }

  public Score score(Incident incident, Event event, OptionalInt depDistance) {
    double t = temporal(event.getTimestamp(), incident.getStartedAt());
    double d = DependencyGraph.scoreForDistance(depDistance);
    double a = clamp(event.getSeverity());
    double e = errorCorrelation(event);
    boolean upstreamOrSelf = depDistance.isPresent();
    double dep = deploymentScore(event, upstreamOrSelf);
    double total = props.getWTemporal() * t
        + props.getWDependency() * d
        + props.getWAnomaly() * a
        + props.getWError() * e
        + props.getWDeployment() * dep;
    List<String> evidence = new ArrayList<>();
    if (t > 0.5) evidence.add("event preceded incident (" + event.getTimestamp() + ")");
    if (d >= 0.8) evidence.add("service is upstream-or-self of symptomatic service");
    else if (d > 0) evidence.add("service is transitively upstream of symptomatic service");
    if (a > 0.5) evidence.add("strong metric anomaly (severity=" + round2(a) + ")");
    if (e > 0.5) evidence.add("error rate lifted after event");
    if (dep > 0) evidence.add("deployment within lookback window");
    return new Score(round4(total), round4(t), round4(d), round4(a), round4(e), round4(dep), evidence);
  }

  public int confidence(double total) {
    return (int) Math.round(100 * clamp(total));
  }

  private Double num(JsonNode n, String... keys) {
    for (String k : keys) {
      if (n.has(k) && n.get(k).isNumber()) return n.get(k).asDouble();
    }
    return null;
  }

  private static double clamp(double v) {
    return Math.min(1.0, Math.max(0.0, v));
  }

  private static double round4(double v) {
    return Math.round(v * 10000.0) / 10000.0;
  }

  private static String round2(double v) {
    return String.valueOf(Math.round(v * 100.0) / 100.0);
  }
}

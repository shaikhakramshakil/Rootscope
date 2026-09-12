package com.rootscope.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;

public class Dtos {

  public static final String SEVERITIES = "LOW|MEDIUM|HIGH|CRITICAL";
  public static final String EVENT_TYPES =
      "LOG_ERROR|METRIC_ANOMALY|ERROR_SPIKE|LATENCY_ANOMALY|TRAFFIC_DROP|CPU_SPIKE|DB_LATENCY|DEPLOYMENT";

  public record ServiceReq(@NotBlank String name, String team, String repository) {}
  public record ServiceRes(Long id, String name, String team, String repository) {}

  public record LogReq(@NotBlank String service, @NotBlank String level,
      @NotBlank String message, @NotNull Instant timestamp) {}
  public record EventRes(Long id, String service, String type, Instant timestamp,
      double severity, String payload) {}
  public record AnomalyReq(@NotBlank String service,
      @NotBlank @Pattern(regexp = EVENT_TYPES) String type,
      @DecimalMin("0.0") @DecimalMax("1.0") double severity,
      @NotNull Instant timestamp, String payload) {}

  public record MetricReq(@NotBlank String service, @NotBlank String metric,
      double value, @NotNull Instant timestamp) {}

  public record DeploymentReq(@NotBlank String service, @NotBlank String version,
      String commit, String author, @NotNull Instant timestamp) {}
  public record DeploymentRes(Long id, String service, String version, String commit,
      String author, Instant timestamp) {}

  public record DependencyReq(@NotBlank String from, @NotBlank String to) {}
  public record DependencyRes(Long id, String from, String to) {}

  public record IncidentReq(@NotBlank String title, @NotBlank String service,
      @NotBlank @Pattern(regexp = SEVERITIES) String severity,
      @NotNull Instant startedAt) {}
  public record ResolveReq(Instant resolvedAt) {}
  public record IncidentSummary(Long id, String title, String severity, String service,
      Instant startedAt, Instant resolvedAt) {}
  public record CorrelationRes(Long eventId, String service, String type, Instant timestamp,
      double score, int confidence, double temporal, double dependency, double anomaly,
      double errorCorr, double deployment, String reason) {}
  public record IncidentRes(Long id, String title, String severity, String service,
      Instant startedAt, List<CorrelationRes> ranking, CorrelationRes trigger) {}

  public record TimelineEntry(String at, String kind, String label, String service) {}
}

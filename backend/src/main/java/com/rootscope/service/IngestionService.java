package com.rootscope.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootscope.model.DependencyEdge;
import com.rootscope.model.Deployment;
import com.rootscope.model.Event;
import com.rootscope.model.EventType;
import com.rootscope.model.ServiceEntity;
import com.rootscope.repo.DependencyRepository;
import com.rootscope.repo.DeploymentRepository;
import com.rootscope.repo.EventRepository;
import com.rootscope.repo.ServiceRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event ingestion. Deployment writes also fan out to a DEPLOYMENT event so
 * the correlation engine has a single candidate source (events table).
 */
@Service
public class IngestionService {

  private final ServiceRepository services;
  private final EventRepository events;
  private final DeploymentRepository deployments;
  private final DependencyRepository dependencies;
  private final AnomalyDetector detector;
  private final ObjectMapper mapper = new ObjectMapper();

  public IngestionService(ServiceRepository services, EventRepository events,
      DeploymentRepository deployments, DependencyRepository dependencies,
      AnomalyDetector detector) {
    this.services = services;
    this.events = events;
    this.deployments = deployments;
    this.dependencies = dependencies;
    this.detector = detector;
  }

  @Transactional
  public ServiceEntity getOrCreateService(String name, String team, String repository) {
    return services.findByName(name)
        .orElseGet(() -> services.save(new ServiceEntity(name, team, repository)));
  }

  @Transactional
  public Event ingestLog(String serviceName, String level, String message, Instant timestamp) {
    ServiceEntity svc = getOrCreateService(serviceName, null, null);
    boolean isError = "ERROR".equalsIgnoreCase(level) || "FATAL".equalsIgnoreCase(level);
    EventType type = isError ? EventType.LOG_ERROR : EventType.METRIC_ANOMALY;
    double severity = isError ? 0.6 : 0.1;
    return events.save(new Event(svc, type, timestamp, severity,
        "{\"level\":\"" + level + "\",\"message\":" + json(message) + "}"));
  }

  @Transactional
  public Event ingestMetric(String serviceName, String metric, double value, Instant timestamp) {
    ServiceEntity svc = getOrCreateService(serviceName, null, null);
    AnomalyDetector.Reading r = detector.observe(serviceName, metric, value);
    if (!r.anomalous()) return null;
    Map<String, Object> payload = new HashMap<>();
    payload.put("metric", metric);
    payload.put("value", value);
    payload.put("reason", r.reason());
    return events.save(new Event(svc, mapMetric(metric), timestamp, r.severity(), write(payload)));
  }

  /** Test/seeding hook: record a metric anomaly with explicit severity + payload. */
  @Transactional
  public Event recordAnomaly(String serviceName, EventType type, double severity,
      Instant timestamp, String payload) {
    ServiceEntity svc = getOrCreateService(serviceName, null, null);
    return events.save(new Event(svc, type, timestamp, severity, payload));
  }

  @Transactional
  public Deployment ingestDeployment(String serviceName, String version, String commit,
      String author, Instant timestamp) {
    ServiceEntity svc = getOrCreateService(serviceName, null, null);
    Deployment d = deployments.save(new Deployment(svc, version, commit, author, timestamp));
    Map<String, Object> payload = new HashMap<>();
    payload.put("version", version);
    payload.put("commit", commit);
    payload.put("author", author);
    events.save(new Event(svc, EventType.DEPLOYMENT, timestamp, 0.5, write(payload)));
    return d;
  }

  @Transactional
  public DependencyEdge addDependency(String from, String to) {
    ServiceEntity f = getOrCreateService(from, null, null);
    ServiceEntity t = getOrCreateService(to, null, null);
    if (f.getId().equals(t.getId())) throw new IllegalArgumentException("self-dependency not allowed");
    if (dependencies.existsByFromIdAndToId(f.getId(), t.getId())) {
      return dependencies.findAll().stream()
          .filter(e -> e.getFrom().getId().equals(f.getId()) && e.getTo().getId().equals(t.getId()))
          .findFirst().orElseThrow();
    }
    return dependencies.save(new DependencyEdge(f, t));
  }

  private static EventType mapMetric(String metric) {
    return switch (metric) {
      case "error_rate" -> EventType.ERROR_SPIKE;
      case "latency_ms" -> EventType.LATENCY_ANOMALY;
      case "db_latency_ms" -> EventType.DB_LATENCY;
      case "request_rate" -> EventType.TRAFFIC_DROP;
      case "cpu" -> EventType.CPU_SPIKE;
      default -> EventType.METRIC_ANOMALY;
    };
  }

  private String write(Object o) {
    try {
      return mapper.writeValueAsString(o);
    } catch (Exception e) {
      return "{}";
    }
  }

  private String json(String s) {
    try {
      return mapper.writeValueAsString(s);
    } catch (Exception e) {
      return "\"\"";
    }
  }
}

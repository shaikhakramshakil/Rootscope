package com.rootscope.web;

import com.rootscope.model.DependencyEdge;
import com.rootscope.model.Deployment;
import com.rootscope.model.Event;
import com.rootscope.model.EventType;
import com.rootscope.model.ServiceEntity;
import com.rootscope.repo.DependencyRepository;
import com.rootscope.repo.ServiceRepository;
import com.rootscope.service.DependencyGraph;
import com.rootscope.service.IngestionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class IngestController {

  private final IngestionService ingestion;
  private final ServiceRepository services;
  private final DependencyRepository dependencies;
  private final DependencyGraph graph;

  public IngestController(IngestionService ingestion, ServiceRepository services,
      DependencyRepository dependencies, DependencyGraph graph) {
    this.ingestion = ingestion;
    this.services = services;
    this.dependencies = dependencies;
    this.graph = graph;
  }

  @PostMapping("/services")
  public Dtos.ServiceRes service(@Valid @RequestBody Dtos.ServiceReq req) {
    ServiceEntity s = ingestion.getOrCreateService(req.name(), req.team(), req.repository());
    return new Dtos.ServiceRes(s.getId(), s.getName(), s.getTeam(), s.getRepository());
  }

  @GetMapping("/services")
  public List<Dtos.ServiceRes> serviceList() {
    return services.findAll().stream()
        .map(s -> new Dtos.ServiceRes(s.getId(), s.getName(), s.getTeam(), s.getRepository()))
        .collect(Collectors.toList());
  }

  @PostMapping("/logs")
  public ResponseEntity<Dtos.EventRes> log(@Valid @RequestBody Dtos.LogReq req) {
    Event e = ingestion.ingestLog(req.service(), req.level(), req.message(), req.timestamp());
    return ResponseEntity.ok(toRes(e));
  }

  /** Backfill / replay path: record an anomaly with explicit severity + payload. */
  @PostMapping("/anomalies")
  public Dtos.EventRes anomaly(@Valid @RequestBody Dtos.AnomalyReq req) {
    Event e = ingestion.recordAnomaly(req.service(), EventType.valueOf(req.type()),
        req.severity(), req.timestamp(), req.payload());
    return toRes(e);
  }

  @PostMapping("/metrics")
  public ResponseEntity<Dtos.EventRes> metric(@Valid @RequestBody Dtos.MetricReq req) {
    Event e = ingestion.ingestMetric(req.service(), req.metric(), req.value(), req.timestamp());
    if (e == null) return ResponseEntity.accepted().build();
    return ResponseEntity.ok(toRes(e));
  }

  @PostMapping("/deployments")
  public Dtos.DeploymentRes deployment(@Valid @RequestBody Dtos.DeploymentReq req) {
    Deployment d = ingestion.ingestDeployment(req.service(), req.version(), req.commit(),
        req.author(), req.timestamp());
    return new Dtos.DeploymentRes(d.getId(), d.getService().getName(), d.getVersion(),
        d.getCommitHash(), d.getAuthor(), d.getTimestamp());
  }

  @PostMapping("/dependencies")
  public Dtos.DependencyRes dependency(@Valid @RequestBody Dtos.DependencyReq req) {
    DependencyEdge e = ingestion.addDependency(req.from(), req.to());
    return new Dtos.DependencyRes(e.getId(), e.getFrom().getName(), e.getTo().getName());
  }

  @GetMapping("/dependencies/graph")
  @Transactional(readOnly = true)
  public List<Dtos.DependencyRes> graph() {
    return dependencies.findAll().stream()
        .map(e -> new Dtos.DependencyRes(e.getId(), e.getFrom().getName(), e.getTo().getName()))
        .collect(Collectors.toList());
  }

  @GetMapping("/dependencies/impact")
  @Transactional(readOnly = true)
  public Set<String> impact(@RequestParam String service) {
    ServiceEntity s = services.findByName(service)
        .orElseThrow(() -> new NoSuchElementException("service " + service + " not found"));
    Set<Long> ids = graph.downstreamImpact(s.getId());
    return services.findAllById(ids).stream().map(ServiceEntity::getName)
        .collect(Collectors.toSet());
  }

  private static Dtos.EventRes toRes(Event e) {
    return new Dtos.EventRes(e.getId(), e.getService().getName(), e.getType().name(),
        e.getTimestamp(), e.getSeverity(), e.getPayload());
  }
}

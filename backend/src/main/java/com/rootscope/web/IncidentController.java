package com.rootscope.web;

import com.rootscope.model.Correlation;
import com.rootscope.model.Incident;
import com.rootscope.model.ServiceEntity;
import com.rootscope.model.Severity;
import com.rootscope.repo.CorrelationRepository;
import com.rootscope.repo.IncidentRepository;
import com.rootscope.repo.ServiceRepository;
import com.rootscope.service.AnalysisService;
import com.rootscope.service.RootCauseScorer;
import com.rootscope.service.TimelineService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

  private final IncidentRepository incidents;
  private final ServiceRepository services;
  private final CorrelationRepository correlations;
  private final AnalysisService analysis;
  private final TimelineService timeline;
  private final RootCauseScorer scorer;

  public IncidentController(IncidentRepository incidents, ServiceRepository services,
      CorrelationRepository correlations, AnalysisService analysis,
      TimelineService timeline, RootCauseScorer scorer) {
    this.incidents = incidents;
    this.services = services;
    this.correlations = correlations;
    this.analysis = analysis;
    this.timeline = timeline;
    this.scorer = scorer;
  }

  @PostMapping
  @Transactional
  public Dtos.IncidentRes create(@Valid @RequestBody Dtos.IncidentReq req) {
    ServiceEntity svc = services.findByName(req.service())
        .orElseThrow(() -> new IllegalArgumentException("unknown service: " + req.service()));
    Incident incident = incidents.save(new Incident(req.title(),
        Severity.valueOf(req.severity()), svc, req.startedAt()));
    List<Correlation> ranking = analysis.analyze(incident.getId());
    return toRes(incident, ranking);
  }

  @GetMapping
  @Transactional(readOnly = true)
  public Page<Dtos.IncidentSummary> list(@PageableDefault(size = 20) Pageable pageable) {
    return incidents.findAllByOrderByStartedAtDesc(pageable).map(IncidentController::toSummary);
  }

  @GetMapping("/{id}")
  @Transactional(readOnly = true)
  public Dtos.IncidentRes get(@PathVariable Long id) {
    Incident incident = incidents.findById(id)
        .orElseThrow(() -> new NoSuchElementException("incident " + id + " not found"));
    return toRes(incident, correlations.findByIncidentIdOrderByScoreDesc(id));
  }

  @PatchMapping("/{id}/resolve")
  @Transactional
  public Dtos.IncidentSummary resolve(@PathVariable Long id,
      @Valid @RequestBody(required = false) Dtos.ResolveReq req) {
    Incident incident = incidents.findById(id)
        .orElseThrow(() -> new NoSuchElementException("incident " + id + " not found"));
    incident.resolve(req == null || req.resolvedAt() == null ? Instant.now() : req.resolvedAt());
    return toSummary(incident);
  }

  @GetMapping("/{id}/timeline")
  public List<Dtos.TimelineEntry> timeline(@PathVariable Long id) {
    return timeline.timeline(id).stream()
        .map(e -> new Dtos.TimelineEntry(e.at(), e.kind(), e.label(), e.service()))
        .collect(Collectors.toList());
  }

  private static Dtos.IncidentSummary toSummary(Incident incident) {
    return new Dtos.IncidentSummary(incident.getId(), incident.getTitle(),
        incident.getSeverity().name(), incident.getService().getName(),
        incident.getStartedAt(), incident.getResolvedAt());
  }

  private Dtos.IncidentRes toRes(Incident incident, List<Correlation> ranking) {
    List<Dtos.CorrelationRes> rs = ranking.stream()
        .map(c -> new Dtos.CorrelationRes(c.getEvent().getId(),
            c.getEvent().getService().getName(), c.getEvent().getType().name(),
            c.getEvent().getTimestamp(), c.getScore(), scorer.confidence(c.getScore()),
            c.getTemporal(), c.getDependency(), c.getAnomaly(), c.getErrorCorr(),
            c.getDeployment(), c.getReason()))
        .collect(Collectors.toList());
    Dtos.CorrelationRes trigger = rs.stream()
        .filter(r -> r.type().equals("DEPLOYMENT") && r.dependency() > 0)
        .findFirst().orElse(null);
    return new Dtos.IncidentRes(incident.getId(), incident.getTitle(),
        incident.getSeverity().name(), incident.getService().getName(),
        incident.getStartedAt(), rs, trigger);
  }
}

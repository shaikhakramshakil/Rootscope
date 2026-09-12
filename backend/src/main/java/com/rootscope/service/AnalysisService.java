package com.rootscope.service;

import com.rootscope.model.Correlation;
import com.rootscope.model.Event;
import com.rootscope.model.Incident;
import com.rootscope.repo.CorrelationRepository;
import com.rootscope.repo.EventRepository;
import com.rootscope.repo.IncidentRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.OptionalInt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Correlation engine: ranks every candidate event for an incident. */
@Service
public class AnalysisService {

  private final IncidentRepository incidents;
  private final EventRepository events;
  private final CorrelationRepository correlations;
  private final DependencyGraph graph;
  private final RootCauseScorer scorer;
  private final ScoringProperties props;

  public AnalysisService(IncidentRepository incidents, EventRepository events,
      CorrelationRepository correlations, DependencyGraph graph,
      RootCauseScorer scorer, ScoringProperties props) {
    this.incidents = incidents;
    this.events = events;
    this.correlations = correlations;
    this.graph = graph;
    this.scorer = scorer;
    this.props = props;
  }

  @Transactional
  public List<Correlation> analyze(Long incidentId) {
    Incident incident = incidents.findById(incidentId)
        .orElseThrow(() -> new NoSuchElementException("incident " + incidentId + " not found"));
    Long symptomaticId = incident.getService().getId();
    Instant from = incident.getStartedAt().minusSeconds(props.getLookbackMinutes() * 60L);
    Instant to = incident.getStartedAt().plusSeconds(5 * 60L);
    List<Event> candidates = events.findByTimestampBetween(from, to);
    Map<Long, Integer> distances = graph.distancesFrom(symptomaticId);
    correlations.deleteByIncidentId(incidentId);
    List<Correlation> out = new ArrayList<>();
    for (Event e : candidates) {
      Integer d = distances.get(e.getService().getId());
      OptionalInt dist = d == null ? OptionalInt.empty() : OptionalInt.of(d);
      RootCauseScorer.Score s = scorer.score(incident, e, dist);
      String reason = String.join("; ", s.evidence());
      out.add(new Correlation(incident, e, s.total(), s.temporal(), s.dependency(),
          s.anomaly(), s.errorCorr(), s.deployment(), reason));
    }
    out.sort(Comparator.comparingDouble(Correlation::getScore).reversed());
    return correlations.saveAll(out);
  }
}

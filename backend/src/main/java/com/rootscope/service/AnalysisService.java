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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Correlation engine: ranks every candidate event for an incident. */
@Service
public class AnalysisService {
  private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
  /** Max candidate events ranked per incident: bounds memory, keeps analyze O(1). */
  private static final int ANALYSIS_BATCH = 2000;

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
    List<Event> candidates =
        events.findByTimestampBetweenOrderByTimestampAsc(from, to, PageRequest.of(0, ANALYSIS_BATCH));
    if (candidates.size() == ANALYSIS_BATCH) {
      log.warn("candidate window truncated at {} events for incident {}", ANALYSIS_BATCH, incidentId);
    }
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

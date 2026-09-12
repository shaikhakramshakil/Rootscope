package com.rootscope.service;

import com.rootscope.model.Correlation;
import com.rootscope.model.Incident;
import com.rootscope.repo.CorrelationRepository;
import com.rootscope.repo.IncidentRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimelineService {

  public record Entry(String at, String kind, String label, String service) {}

  private final IncidentRepository incidents;
  private final CorrelationRepository correlations;

  public TimelineService(IncidentRepository incidents, CorrelationRepository correlations) {
    this.incidents = incidents;
    this.correlations = correlations;
  }

  @Transactional(readOnly = true)
  public List<Entry> timeline(Long incidentId) {
    Incident incident = incidents.findById(incidentId)
        .orElseThrow(() -> new NoSuchElementException("incident " + incidentId + " not found"));
    List<Entry> out = new ArrayList<>();
    for (Correlation c : correlations.findByIncidentIdOrderByScoreDesc(incidentId)) {
      out.add(new Entry(c.getEvent().getTimestamp().toString(), c.getEvent().getType().name(),
          describe(c), c.getEvent().getService().getName()));
    }
    out.add(new Entry(incident.getStartedAt().toString(), "INCIDENT",
        incident.getTitle() + " [" + incident.getSeverity() + "]",
        incident.getService().getName()));
    out.sort(Comparator.comparing(Entry::at));
    return out;
  }

  private String describe(Correlation c) {
    String svc = c.getEvent().getService().getName();
    return switch (c.getEvent().getType()) {
      case DEPLOYMENT -> "Deployment " + svc + " (score=" + c.getScore() + ")";
      case ERROR_SPIKE -> "Error spike " + svc + " (score=" + c.getScore() + ")";
      case LATENCY_ANOMALY, DB_LATENCY -> "Latency anomaly " + svc;
      case TRAFFIC_DROP -> "Traffic drop " + svc;
      case CPU_SPIKE -> "CPU spike " + svc;
      default -> c.getEvent().getType() + " " + svc;
    };
  }
}

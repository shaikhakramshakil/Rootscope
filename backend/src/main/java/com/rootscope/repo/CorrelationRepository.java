package com.rootscope.repo;

import com.rootscope.model.Correlation;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CorrelationRepository extends JpaRepository<Correlation, Long> {
  @EntityGraph(attributePaths = {"event", "event.service"})
  List<Correlation> findByIncidentIdOrderByScoreDesc(Long incidentId);

  void deleteByIncidentId(Long incidentId);
}

package com.rootscope.repo;

import com.rootscope.model.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
  @EntityGraph(attributePaths = {"service"})
  Page<Incident> findAllByOrderByStartedAtDesc(Pageable pageable);
}

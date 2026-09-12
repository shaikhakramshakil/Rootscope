package com.rootscope.repo;

import com.rootscope.model.Event;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
  @EntityGraph(attributePaths = {"service"})
  List<Event> findByTimestampBetween(Instant from, Instant to);
}

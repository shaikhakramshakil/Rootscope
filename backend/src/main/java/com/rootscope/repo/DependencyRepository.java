package com.rootscope.repo;

import com.rootscope.model.DependencyEdge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DependencyRepository extends JpaRepository<DependencyEdge, Long> {
  List<DependencyEdge> findAll();
  boolean existsByFromIdAndToId(Long fromId, Long toId);
}

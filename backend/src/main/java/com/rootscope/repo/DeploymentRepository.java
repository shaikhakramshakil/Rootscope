package com.rootscope.repo;

import com.rootscope.model.Deployment;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
  List<Deployment> findByServiceIdAndTimestampBetween(Long serviceId, Instant from, Instant to);
  List<Deployment> findByTimestampBetween(Instant from, Instant to);
}

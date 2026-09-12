package com.rootscope.repo;

import com.rootscope.model.ServiceEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {
  Optional<ServiceEntity> findByName(String name);
}

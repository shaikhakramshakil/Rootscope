package com.rootscope.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "dependencies",
    uniqueConstraints = @UniqueConstraint(columnNames = {"from_service_id", "to_service_id"}))
public class DependencyEdge {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Dependent (downstream). from depends on to. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "from_service_id", nullable = false)
  private ServiceEntity from;

  /** Upstream dependency. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "to_service_id", nullable = false)
  private ServiceEntity to;

  protected DependencyEdge() {}

  public DependencyEdge(ServiceEntity from, ServiceEntity to) {
    this.from = from;
    this.to = to;
  }

  public Long getId() { return id; }
  public ServiceEntity getFrom() { return from; }
  public ServiceEntity getTo() { return to; }
}

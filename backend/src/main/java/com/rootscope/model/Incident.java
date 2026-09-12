package com.rootscope.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "incidents")
public class Incident {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Severity severity;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "service_id", nullable = false)
  private ServiceEntity service;

  @Column(nullable = false)
  private Instant startedAt;

  private Instant resolvedAt;

  protected Incident() {}

  public Incident(String title, Severity severity, ServiceEntity service, Instant startedAt) {
    this.title = title;
    this.severity = severity;
    this.service = service;
    this.startedAt = startedAt;
  }

  public void resolve(Instant resolvedAt) {
    this.resolvedAt = resolvedAt;
  }

  public Long getId() { return id; }
  public String getTitle() { return title; }
  public Severity getSeverity() { return severity; }
  public ServiceEntity getService() { return service; }
  public Instant getStartedAt() { return startedAt; }
  public Instant getResolvedAt() { return resolvedAt; }
}

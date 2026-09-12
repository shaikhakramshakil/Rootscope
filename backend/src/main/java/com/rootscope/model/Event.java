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
@Table(name = "events")
public class Event {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "service_id", nullable = false)
  private ServiceEntity service;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EventType type;

  @Column(name = "occurred_at", nullable = false)
  private Instant timestamp;

  /** Normalized 0..1 anomaly/error strength set at ingestion. 0 when not applicable. */
  @Column(nullable = false)
  private double severity;

  /** Raw JSON payload (metric name/value, error counts, deployment version...). TEXT for H2/PG compat. */
  @Column(columnDefinition = "TEXT")
  private String payload;

  protected Event() {}

  public Event(ServiceEntity service, EventType type, Instant timestamp, double severity,
      String payload) {
    this.service = service;
    this.type = type;
    this.timestamp = timestamp;
    this.severity = severity;
    this.payload = payload;
  }

  public Long getId() { return id; }
  public ServiceEntity getService() { return service; }
  public EventType getType() { return type; }
  public Instant getTimestamp() { return timestamp; }
  public double getSeverity() { return severity; }
  public String getPayload() { return payload; }
}

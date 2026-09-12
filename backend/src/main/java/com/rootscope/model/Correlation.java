package com.rootscope.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "correlations")
public class Correlation {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "incident_id", nullable = false)
  private Incident incident;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "event_id", nullable = false)
  private Event event;

  @Column(nullable = false)
  private double score;

  @Column(nullable = false)
  private double temporal;

  @Column(nullable = false)
  private double dependency;

  @Column(nullable = false)
  private double anomaly;

  @Column(nullable = false)
  private double errorCorr;

  @Column(nullable = false)
  private double deployment;

  @Column(columnDefinition = "TEXT")
  private String reason;

  protected Correlation() {}

  public Correlation(Incident incident, Event event, double score,
      double temporal, double dependency, double anomaly, double errorCorr,
      double deployment, String reason) {
    this.incident = incident;
    this.event = event;
    this.score = score;
    this.temporal = temporal;
    this.dependency = dependency;
    this.anomaly = anomaly;
    this.errorCorr = errorCorr;
    this.deployment = deployment;
    this.reason = reason;
  }

  public Long getId() { return id; }
  public Incident getIncident() { return incident; }
  public Event getEvent() { return event; }
  public double getScore() { return score; }
  public double getTemporal() { return temporal; }
  public double getDependency() { return dependency; }
  public double getAnomaly() { return anomaly; }
  public double getErrorCorr() { return errorCorr; }
  public double getDeployment() { return deployment; }
  public String getReason() { return reason; }
}

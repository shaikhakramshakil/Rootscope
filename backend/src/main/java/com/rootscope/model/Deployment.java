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
import java.time.Instant;

@Entity
@Table(name = "deployments")
public class Deployment {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "service_id", nullable = false)
  private ServiceEntity service;

  @Column(nullable = false)
  private String version;

  private String commitHash;
  private String author;

  @Column(name = "occurred_at", nullable = false)
  private Instant timestamp;

  protected Deployment() {}

  public Deployment(ServiceEntity service, String version, String commitHash, String author,
      Instant timestamp) {
    this.service = service;
    this.version = version;
    this.commitHash = commitHash;
    this.author = author;
    this.timestamp = timestamp;
  }

  public Long getId() { return id; }
  public ServiceEntity getService() { return service; }
  public String getVersion() { return version; }
  public String getCommitHash() { return commitHash; }
  public String getAuthor() { return author; }
  public Instant getTimestamp() { return timestamp; }
}

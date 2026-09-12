package com.rootscope.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "services", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class ServiceEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  private String team;
  private String repository;

  protected ServiceEntity() {}

  public ServiceEntity(String name, String team, String repository) {
    this.name = name;
    this.team = team;
    this.repository = repository;
  }

  public Long getId() { return id; }
  public String getName() { return name; }
  public String getTeam() { return team; }
  public String getRepository() { return repository; }
}

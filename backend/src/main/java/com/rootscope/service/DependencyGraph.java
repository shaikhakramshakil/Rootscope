package com.rootscope.service;

import com.rootscope.model.DependencyEdge;
import com.rootscope.repo.DependencyRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * In-memory view of the service dependency graph.
 *
 * <p>Edge {@code from -> to} means "from depends on to" (to is upstream).
 * A candidate C explains a symptom at S when C is reachable from S by
 * following depends-on edges (S -> ... -> C), i.e. C is upstream-or-self.
 */
@Service
public class DependencyGraph {

  private final DependencyRepository repo;

  public DependencyGraph(DependencyRepository repo) {
    this.repo = repo;
  }

  /**
   * Upstream distances from the symptomatic service to every reachable
   * service (itself included at 0). One BFS over one adjacency snapshot —
   * prefer over per-candidate {@link #upstreamDistance} in bulk scoring.
   */
  public Map<Long, Integer> distancesFrom(Long symptomaticId) {
    Map<Long, List<Long>> adj = adjacency();
    Map<Long, Integer> dist = new HashMap<>();
    Queue<Long> q = new ArrayDeque<>();
    dist.put(symptomaticId, 0);
    q.add(symptomaticId);
    while (!q.isEmpty()) {
      Long cur = q.poll();
      int d = dist.get(cur);
      for (Long next : adj.getOrDefault(cur, List.of())) {
        if (!dist.containsKey(next)) {
          dist.put(next, d + 1);
          q.add(next);
        }
      }
    }
    return dist;
  }

  /** Upstream distance from symptomatic service to candidate. Empty = unreachable. */
  public OptionalInt upstreamDistance(Long symptomaticId, Long candidateId) {
    if (symptomaticId.equals(candidateId)) return OptionalInt.of(0);
    Integer d = distancesFrom(symptomaticId).get(candidateId);
    return d == null ? OptionalInt.empty() : OptionalInt.of(d);
  }

  /** Dependency component score from BFS distance. Documented in docs/ALGORITHM.md. */
  public static double scoreForDistance(OptionalInt distance) {
    if (distance.isEmpty()) return 0.0;
    return switch (distance.getAsInt()) {
      case 0 -> 1.0;
      case 1 -> 0.8;
      case 2 -> 0.6;
      case 3 -> 0.4;
      default -> 0.2;
    };
  }

  /** All downstream services that would feel an outage at {@code serviceId}. */
  public Set<Long> downstreamImpact(Long serviceId) {
    Map<Long, List<Long>> reverse = new HashMap<>();
    for (DependencyEdge e : repo.findAll()) {
      reverse.computeIfAbsent(e.getTo().getId(), k -> new ArrayList<>()).add(e.getFrom().getId());
    }
    Set<Long> seen = new HashSet<>();
    Queue<Long> q = new ArrayDeque<>();
    q.add(serviceId);
    seen.add(serviceId);
    while (!q.isEmpty()) {
      for (Long next : reverse.getOrDefault(q.poll(), List.of())) {
        if (seen.add(next)) q.add(next);
      }
    }
    seen.remove(serviceId);
    return seen;
  }

  private Map<Long, List<Long>> adjacency() {
    Map<Long, List<Long>> adj = new HashMap<>();
    for (DependencyEdge e : repo.findAll()) {
      adj.computeIfAbsent(e.getFrom().getId(), k -> new ArrayList<>()).add(e.getTo().getId());
    }
    return adj;
  }
}

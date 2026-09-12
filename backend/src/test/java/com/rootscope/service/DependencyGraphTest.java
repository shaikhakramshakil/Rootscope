package com.rootscope.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rootscope.model.ServiceEntity;
import com.rootscope.model.DependencyEdge;
import com.rootscope.repo.DependencyRepository;
import com.rootscope.repo.ServiceRepository;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class DependencyGraphTest {

  @Autowired ServiceRepository services;
  @Autowired DependencyRepository edges;

  private ServiceEntity svc(String name) {
    return services.save(new ServiceEntity(name, null, null));
  }

  @Test
  void bulkDistancesCoverChainAndExcludeUnrelated() {
    ServiceEntity frontend = svc("g-frontend");
    ServiceEntity checkout = svc("g-checkout");
    ServiceEntity payment = svc("g-payment");
    ServiceEntity database = svc("g-database");
    ServiceEntity outsider = svc("g-outsider");
    edges.save(new DependencyEdge(frontend, checkout));
    edges.save(new DependencyEdge(checkout, payment));
    edges.save(new DependencyEdge(payment, database));

    DependencyGraph graph = new DependencyGraph(edges);
    Map<Long, Integer> dist = graph.distancesFrom(checkout.getId());

    assertEquals(0, dist.get(checkout.getId()));
    assertEquals(1, dist.get(payment.getId()));
    assertEquals(2, dist.get(database.getId()));
    assertFalse(dist.containsKey(frontend.getId()));
    assertFalse(dist.containsKey(outsider.getId()));

    assertEquals(OptionalInt.of(2),
        graph.upstreamDistance(checkout.getId(), database.getId()));
    assertEquals(OptionalInt.empty(),
        graph.upstreamDistance(checkout.getId(), outsider.getId()));
    assertTrue(graph.downstreamImpact(payment.getId()).contains(checkout.getId()));
  }
}

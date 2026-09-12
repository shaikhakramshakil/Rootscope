package com.rootscope;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rootscope.model.EventType;
import com.rootscope.service.IngestionService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end PRD scenario: payment v4 deploy -> db latency -> payment errors
 * -> checkout incident. The deployment must rank first.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IncidentFlowTest {

  @Autowired MockMvc mvc;
  @Autowired IngestionService ingestion;

  @Test
  void paymentExplainsCheckoutIncident() throws Exception {
    Instant t31 = Instant.parse("2026-09-12T14:31:00Z");
    Instant t32 = Instant.parse("2026-09-12T14:32:00Z");
    Instant t33 = Instant.parse("2026-09-12T14:33:00Z");

    ingestion.addDependency("frontend", "checkout");
    ingestion.addDependency("checkout", "payment");
    ingestion.addDependency("payment", "database");

    ingestion.ingestDeployment("payment", "v4", "abc123", "akram", t31);
    ingestion.recordAnomaly("payment", EventType.DB_LATENCY, 0.7, t32,
        "{\"metric\":\"db_latency_ms\",\"errorRateBefore\":0.01,\"errorRateAfter\":0.02}");
    ingestion.recordAnomaly("payment", EventType.ERROR_SPIKE, 0.9, t33,
        "{\"errorRateBefore\":0.01,\"errorRateAfter\":0.05}");

    mvc.perform(post("/api/v1/incidents")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"Checkout failures","service":"checkout",
                 "severity":"HIGH","startedAt":"2026-09-12T14:34:00Z"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ranking").isArray())
        .andExpect(jsonPath("$.ranking[0].service").value("payment"))
        .andExpect(jsonPath("$.trigger.service").value("payment"))
        .andExpect(jsonPath("$.trigger.type").value("DEPLOYMENT"));
  }
}

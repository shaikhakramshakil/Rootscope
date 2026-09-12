package com.rootscope;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

@SpringBootTest
@AutoConfigureMockMvc
class IncidentApiTest {

  @Autowired MockMvc mvc;
  @Autowired IngestionService ingestion;

  private void seed(String svc, String upstream) {
    ingestion.addDependency("web-" + svc, svc);
    ingestion.addDependency(svc, upstream);
    ingestion.ingestDeployment(upstream, "v1", "c0ffee", "sam",
        Instant.parse("2026-09-12T14:31:00Z"));
    ingestion.recordAnomaly(upstream, EventType.ERROR_SPIKE, 0.8,
        Instant.parse("2026-09-12T14:33:00Z"),
        "{\"errorRateBefore\":0.01,\"errorRateAfter\":0.04}");
  }

  private String incidentJson(String svc) {
    return "{\"title\":\"" + svc + " down\",\"service\":\"" + svc
        + "\",\"severity\":\"HIGH\",\"startedAt\":\"2026-09-12T14:34:00Z\"}";
  }

  @Test
  void listContainsCreatedIncident() throws Exception {
    seed("shop", "cart");
    mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON)
            .content(incidentJson("shop")))
        .andExpect(status().isOk());

    mvc.perform(get("/api/v1/incidents?size=50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.service == 'shop')]").isArray())
        .andExpect(jsonPath("$.content[?(@.service == 'shop')]").isNotEmpty());
  }

  @Test
  void resolveSetsResolvedAt() throws Exception {
    seed("till", "ledger");
    String body = mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON)
            .content(incidentJson("till")))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    long id = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();

    mvc.perform(patch("/api/v1/incidents/" + id + "/resolve")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"resolvedAt\":\"2026-09-12T15:00:00Z\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resolvedAt").value("2026-09-12T15:00:00Z"));
  }

  @Test
  void unknownIncidentReturns404Problem() throws Exception {
    mvc.perform(get("/api/v1/incidents/999999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
    mvc.perform(get("/api/v1/incidents/999999/timeline"))
        .andExpect(status().isNotFound());
    mvc.perform(patch("/api/v1/incidents/999999/resolve")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void invalidSeverityIs400WithFieldErrors() throws Exception {
    mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"x\",\"service\":\"shop\",\"severity\":\"BOGUS\","
                + "\"startedAt\":\"2026-09-12T14:34:00Z\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors.severity").exists());
  }

  @Test
  void unknownServiceIs400() throws Exception {
    mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON)
            .content(incidentJson("no-such-service")))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/dependencies/impact?service=no-such-service"))
        .andExpect(status().isNotFound());
  }

  @Test
  void outOfRangeSeverityIs400() throws Exception {
    mvc.perform(post("/api/v1/anomalies").contentType(MediaType.APPLICATION_JSON)
            .content("{\"service\":\"shop\",\"type\":\"ERROR_SPIKE\",\"severity\":2.5,"
                + "\"timestamp\":\"2026-09-12T14:33:00Z\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void malformedJsonIs400() throws Exception {
    mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON)
            .content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }
}

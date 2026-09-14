# RootScope — Production Incident Intelligence Platform
**Live demo:** https://shaikhakramshakil-rootscope-demo.static.hf.space (scripted incident, no backend needed)

[![CI](https://github.com/shaikhakramshakil/Rootscope/actions/workflows/ci.yml/badge.svg)](https://github.com/shaikhakramshakil/Rootscope/actions/workflows/ci.yml)
![Java 17](https://img.shields.io/badge/java-17-blue)
![License: MIT](https://img.shields.io/badge/license-MIT-green)

When production breaks, the hard problem isn't collecting data — it's
answering **what actually caused the incident**. RootScope correlates logs,
metrics, deployments, and service dependencies into a ranked list of probable
causes, each with a score derived from a documented algorithm and the evidence
behind it.

```
14:31  payment-service v4 deployed
14:32  DB latency increases                  ┐
14:33  payment API errors increase           │ RootScope ranks every event
14:34  checkout failures increase            │ in the lookback window and
14:35  alerts fire                           ┘ returns the probable cause
                                              + the deployment to roll back
```

## Features

- **Log & metric ingestion** — JSON logs over REST, metric samples with a
  rolling z-score/threshold anomaly detector, or JSON envelopes over Kafka.
- **Deployment correlation** — deploys fan out to candidate events, so code
  changes are first-class suspects.
- **Service dependency graph** — BFS-based upstream scoring plus downstream
  blast-radius queries, separating symptom from cause.
- **Root-cause ranking** — `w1·temporal + w2·dependency + w3·anomaly +
  w4·error + w5·deployment`, fully derived in [`docs/ALGORITHM.md`](docs/ALGORITHM.md).
  Confidence numbers come from the formula, never from hand-waving.
- **Cause vs. trigger** — the top event (often the proximate symptom) plus
  the top upstream deployment (the change to roll back).
- **Incident timeline** — auto-built from ranked events.
- **Dashboard** — incident view with evidence, full score breakdown, timeline.
- **Ops-ready** — Flyway migrations, RFC 9457 errors, OpenAPI/Swagger UI,
  Actuator health + Prometheus metrics, Docker Compose, CI.

## Quickstart

Prerequisites: JDK 17+, Node 22+.

```bash
# backend (H2 in-memory, Flyway migrates on boot) → http://localhost:8080
cd backend && mvn spring-boot:run

# seed the PRD demo scenario and print the ranking (new terminal)
./seed.sh

# dashboard → http://localhost:5173
cd frontend && npm install && npm run dev
```

Swagger UI: `http://localhost:8080/swagger-ui.html` ·
Health: `http://localhost:8080/actuator/health` ·
Metrics: `http://localhost:8080/actuator/prometheus`

Full stack with Postgres + Kafka (run from `infra/`):

```bash
cd infra && docker compose up --build
```

## Configuration

| Variable | Default | Purpose |
| -------- | ------- | ------- |
| `SPRING_DATASOURCE_URL` | H2 mem (`prod`: postgres) | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | `sa`/empty (`prod`: `rootscope`) | DB credentials |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | unset | Kafka brokers |
| `ROOTSCOPE_KAFKA_ENABLED` | `false` (`prod` compose: `true`) | Kafka ingest listener |

Scoring weights live under `rootscope.scoring` in `application.yml`
(must sum to 1 — validated at startup).

## API

| Method | Path | Purpose |
| ------ | ---- | ------- |
| POST | `/api/v1/services` | register a service |
| GET | `/api/v1/services` | list services |
| POST | `/api/v1/logs` | ingest a log (`ERROR` → candidate event) |
| POST | `/api/v1/metrics` | ingest a metric sample (`202` = no anomaly) |
| POST | `/api/v1/anomalies` | backfill an anomaly with explicit severity |
| POST | `/api/v1/deployments` | record a deploy (+ candidate event) |
| POST | `/api/v1/dependencies` | add a depends-on edge |
| GET | `/api/v1/dependencies/graph` | all edges |
| GET | `/api/v1/dependencies/impact?service=X` | downstream blast radius |
| POST | `/api/v1/incidents` | open + analyze (ranking + trigger) |
| GET | `/api/v1/incidents` | paged incident list |
| GET | `/api/v1/incidents/{id}` | incident + ranking + trigger |
| PATCH | `/api/v1/incidents/{id}/resolve` | mark resolved |
| GET | `/api/v1/incidents/{id}/timeline` | auto-built timeline |

Errors follow [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html)
(`application/problem+json`): `400` for validation/unknown references,
`404` for missing resources.

### Kafka ingestion

Publish envelopes to `rootscope-ingest` (see `KafkaIngestListener` for kinds
`SERVICE|LOG|METRIC|ANOMALY|DEPLOYMENT|DEPENDENCY`):

```json
{"kind":"DEPLOYMENT","service":"payment","version":"v4",
 "commit":"abc123","author":"akram","timestamp":"2026-09-12T14:31:00Z"}
```

## Development

```bash
cd backend && mvn verify     # unit + integration tests (H2 + Flyway)
cd frontend && npm test      # vitest suite
cd frontend && npm run build # typecheck + production build
```

## Project structure

```
backend/    Spring Boot 3 API: web, service (scorer, detector, graph,
            ingestion, kafka), model, repo + Flyway migrations
frontend/   React + TypeScript dashboard (Vite, Vitest)
infra/      Docker Compose: postgres, kafka, backend, frontend
docs/       ALGORITHM.md — the scoring math with a worked example
seed.sh     PRD demo scenario via curl
```

## Roadmap

- Joint candidate×incident error-series correlation (today: single-source lift).
- OpenSearch log sink and Prometheus remote-write source (today: REST/Kafka in).
- Failure/load/chaos suites per the TRD testing strategy.

## License

MIT — see [LICENSE](LICENSE).

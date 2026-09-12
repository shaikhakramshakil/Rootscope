# RootScope scoring algorithm

Every candidate event gets one number in `[0, 1]`:

```
RootCauseScore =
    w1 × temporalCorrelation
  + w2 × dependencyImpact
  + w3 × anomalyStrength
  + w4 × errorCorrelation
  + w5 × deploymentCorrelation
```

Default weights (`backend/src/main/resources/application.yml`, all tunable):

| weight | value | component |
| ------ | ----- | --------- |
| w1 | 0.25 | temporalCorrelation |
| w2 | 0.20 | dependencyImpact |
| w3 | 0.20 | anomalyStrength |
| w4 | 0.20 | errorCorrelation |
| w5 | 0.15 | deploymentCorrelation |

Confidence shown in the UI is `round(100 × RootCauseScore)` — derived from
the formula, never a hand-picked number.

## 1. Temporal correlation — `exp(-dt / τ)`

`dt` = minutes from event to incident start. `τ = 15 min`, lookback = 60 min.

- `dt = 0` → 1.0. `dt = τ` → 0.368. `dt = 60` → 0.018.
- Events after incident start, or older than the lookback, score **0**
  (an effect cannot precede its cause in this model).

## 2. Dependency impact — BFS distance on the depends-on graph

Edge `A → B` means "A depends on B" (B is upstream). From the symptomatic
service we BFS along depends-on edges; distance `d` to the candidate:

| d | meaning | score |
| - | ------- | ----- |
| 0 | same service | 1.0 |
| 1 | direct dependency | 0.8 |
| 2 | transitive (2 hops) | 0.6 |
| 3 | transitive (3 hops) | 0.4 |
| ≥4 | reachable but far | 0.2 |
| — | unreachable | 0.0 |

Unreachable services can still rank via the other four components, but they
can never win on graph grounds. `GET /api/v1/dependencies/impact?service=X`
lists downstream blast radius (reverse BFS) to separate symptom from cause.

## 3. Anomaly strength — normalized detector severity

`0..1` severity written at ingestion by `AnomalyDetector`
(`min(1, max(z/6, jumpRatio))`, per-metric thresholds for error rate,
latency, traffic, CPU, DB latency — see `AnomalyDetector.checkValue`).
Backfilled events carry an explicit severity. No anomaly data → 0.

## 4. Error correlation — relative error lift in the event payload

```
before, after = payload errorRateBefore/errorRateAfter (or countBefore/After)
before ≈ 0  →  after > 0 ? 1.0 : 0.0
else        →  clamp((after - before) / before / 3)
```

A 4× error lift scores 1.0; flat errors score 0. Events without error
fields score 0, except `ERROR_SPIKE` which falls back to its severity.
(Single-source lift is a proxy for true cross-series correlation; joint
candidate×incident series correlation is roadmap.)

## 5. Deployment correlation — is this a related code change?

- `DEPLOYMENT` event on an upstream-or-self service → 1.0
- `DEPLOYMENT` elsewhere → 0.5 (a change happened, but graph says unrelated)
- non-deployment → 0.0

Recency is already handled by the temporal term, so this term is binary
by design. Deployments fan out to `DEPLOYMENT` events at ingestion, so the
engine has a single candidate source.

## Trigger vs. top candidate

The ranking mixes symptoms and causes: an error spike 1 minute before the
incident usually outscores the deployment that caused it. The API therefore
returns both:

- `ranking[0]` — highest-scoring event (often the proximate symptom),
- `trigger` — highest-scoring `DEPLOYMENT` on an upstream-or-self service
  (the actionable change to roll back first).

## Worked example (the PRD scenario)

Graph: `frontend → checkout → payment → database`. Incident: checkout
failures @ 14:34.

| event | temp | dep | anom | err | depl | total | conf |
| ----- | ---- | --- | ---- | --- | ---- | ----- | ---- |
| payment ERROR_SPIKE @ 14:33 (1%→5%) | 0.9355 | 0.8 | 0.9 | 1.0 | 0 | **0.7739** | **77%** |
| payment v4 DEPLOYMENT @ 14:31 | 0.8187 | 0.8 | 0.5 | 0 | 1.0 | **0.6147** | **61%** |
| payment DB_LATENCY @ 14:32 | 0.8752 | 0.8 | 0.7 | 0.3333 | 0 | **0.5855** | **59%** |

Headline: payment error spike (77%). Trigger: payment v4 deployment (61%).
Same numbers are asserted in `IncidentFlowTest` and reproduced by `seed.sh`.

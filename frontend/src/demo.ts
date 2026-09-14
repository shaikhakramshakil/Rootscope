import type { Incident, TimelineEntry } from './api';

export const DEMO_MODE: boolean = import.meta.env.VITE_DEMO === '1';

/**
 * Offline demo incident (VITE_DEMO=1 build only): the PRD payment-v4 story
 * with a precomputed ranking, so the dashboard works with no backend.
 */
export const DEMO_INCIDENT: Incident = {
  id: 7,
  title: 'Checkout failures',
  severity: 'HIGH',
  service: 'checkout',
  startedAt: '2026-09-12T14:34:00Z',
  ranking: [
    {
      eventId: 3,
      service: 'payment',
      type: 'ERROR_SPIKE',
      timestamp: '2026-09-12T14:33:00Z',
      score: 0.7739,
      confidence: 77,
      temporal: 0.9355,
      dependency: 0.8,
      anomaly: 0.9,
      errorCorr: 1.0,
      deployment: 0.0,
      reason: 'event preceded incident; service is upstream-or-self of symptomatic service',
    },
    {
      eventId: 2,
      service: 'payment',
      type: 'DB_LATENCY',
      timestamp: '2026-09-12T14:32:00Z',
      score: 0.6125,
      confidence: 61,
      temporal: 0.8752,
      dependency: 0.8,
      anomaly: 0.7,
      errorCorr: 0.33,
      deployment: 0.0,
      reason: 'event preceded incident; service is upstream-or-self of symptomatic service',
    },
    {
      eventId: 1,
      service: 'payment',
      type: 'DEPLOYMENT',
      timestamp: '2026-09-12T14:31:00Z',
      score: 0.585,
      confidence: 59,
      temporal: 0.8187,
      dependency: 0.8,
      anomaly: 0.5,
      errorCorr: 0.0,
      deployment: 1.0,
      reason: 'event preceded incident; deployment within lookback window',
    },
  ],
  trigger: null,
};

export const DEMO_TIMELINE: TimelineEntry[] = [
  { at: '2026-09-12T14:31:00Z', kind: 'DEPLOYMENT', label: 'payment v4 (abc123) by akram', service: 'payment' },
  { at: '2026-09-12T14:32:00Z', kind: 'DB_LATENCY', label: 'db_latency_ms anomaly (severity 0.7)', service: 'payment' },
  { at: '2026-09-12T14:33:00Z', kind: 'ERROR_SPIKE', label: 'error_rate 1% → 5% (severity 0.9)', service: 'payment' },
  { at: '2026-09-12T14:34:00Z', kind: 'INCIDENT', label: 'Checkout failures declared (HIGH)', service: 'checkout' },
];

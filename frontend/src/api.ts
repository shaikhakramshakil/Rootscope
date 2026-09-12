const BASE: string = import.meta.env.VITE_API_BASE ?? '';

export interface Correlation {
  eventId: number;
  service: string;
  type: string;
  timestamp: string;
  score: number;
  confidence: number;
  temporal: number;
  dependency: number;
  anomaly: number;
  errorCorr: number;
  deployment: number;
  reason: string;
}

export interface Incident {
  id: number;
  title: string;
  severity: string;
  service: string;
  startedAt: string;
  ranking: Correlation[];
  trigger: Correlation | null;
}

export interface TimelineEntry {
  at: string;
  kind: string;
  label: string;
  service: string;
}

interface Problem {
  title?: string;
  detail?: string;
  errors?: Record<string, string>;
}

async function toError(res: Response): Promise<Error> {
  const raw = await res.text().catch(() => '');
  try {
    const body = JSON.parse(raw) as Problem;
    const fields = body.errors
      ? Object.entries(body.errors)
          .map(([k, v]) => `${k}: ${v}`)
          .join('; ')
      : '';
    const message = [body.title, body.detail, fields].filter(Boolean).join(' — ');
    return new Error(message || `${res.status}`);
  } catch {
    return new Error(raw || `${res.status}`);
  }
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) throw await toError(res);
  return res.json() as Promise<T>;
}

export const api = {
  createIncident: (body: object) =>
    fetch(`${BASE}/api/v1/incidents`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }).then(json<Incident>),
  getIncident: (id: number) =>
    fetch(`${BASE}/api/v1/incidents/${id}`).then(json<Incident>),
  timeline: (id: number) =>
    fetch(`${BASE}/api/v1/incidents/${id}/timeline`).then(json<TimelineEntry[]>),
  post: (path: string, body: object) =>
    fetch(`${BASE}/api/v1${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }).then((r) => (r.status === 202 ? null : json<unknown>(r))),
};

/** Seeds the PRD demo: payment v4 -> db latency -> payment errors -> checkout incident. */
export async function seedDemo(): Promise<Incident> {
  const t = (m: string) => `2026-09-12T${m}:00Z`;
  for (const [from, to] of [['frontend', 'checkout'], ['checkout', 'payment'], ['payment', 'database']]) {
    await api.post('/dependencies', { from, to });
  }
  await api.post('/deployments', { service: 'payment', version: 'v4', commit: 'abc123', author: 'akram', timestamp: t('14:31') });
  await api.post('/anomalies', { service: 'payment', type: 'DB_LATENCY', severity: 0.7,
    timestamp: t('14:32'), payload: '{"metric":"db_latency_ms","errorRateBefore":0.01,"errorRateAfter":0.02}' });
  await api.post('/anomalies', { service: 'payment', type: 'ERROR_SPIKE', severity: 0.9,
    timestamp: t('14:33'), payload: '{"errorRateBefore":0.01,"errorRateAfter":0.05}' });
  return api.createIncident({ title: 'Checkout failures', service: 'checkout', severity: 'HIGH', startedAt: t('14:34') });
}

import { useState } from 'react';
import { api, seedDemo, type Correlation, type Incident, type TimelineEntry } from './api';
import './styles.css';

function Score({ c }: { c: Correlation }) {
  return (
    <span className="score" title={`temporal=${c.temporal} dependency=${c.dependency} anomaly=${c.anomaly} error=${c.errorCorr} deploy=${c.deployment}`}>
      {(c.score * 100).toFixed(1)}% ({c.confidence}%)
    </span>
  );
}

export default function App() {
  const [incident, setIncident] = useState<Incident | null>(null);
  const [timeline, setTimeline] = useState<TimelineEntry[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [lookupId, setLookupId] = useState('');

  async function load(id: number) {
    setBusy(true);
    setError(null);
    try {
      const [inc, tl] = await Promise.all([api.getIncident(id), api.timeline(id)]);
      setIncident(inc);
      setTimeline(tl);
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }

  async function demo() {
    setBusy(true);
    setError(null);
    try {
      const inc = await seedDemo();
      const tl = await api.timeline(inc.id);
      setIncident(inc);
      setTimeline(tl);
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }

  const top = incident?.ranking[0];

  return (
    <div className="wrap">
      <header>
        <h1>RootScope</h1>
        <p>Production incident intelligence — probable cause, with evidence.</p>
      </header>

      <div className="actions">
        <button disabled={busy} onClick={demo}>Load PRD demo (payment v4 incident)</button>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (/^\d+$/.test(lookupId.trim())) load(Number(lookupId));
            else setError('Incident id must be a number');
          }}
        >
          <input
            placeholder="incident id"
            value={lookupId}
            onChange={(e) => setLookupId(e.target.value)}
          />
          <button disabled={busy || !lookupId} type="submit">Open</button>
        </form>
      </div>

      {error && <pre className="error">{error}</pre>}

      {incident && (
        <main>
          <section className="card incident">
            <h2>INCIDENT #{incident.id} — {incident.title}</h2>
            <div className="meta">
              <span>Severity: <b>{incident.severity}</b></span>
              <span>Service: <b>{incident.service}</b></span>
              <span>Started: <b>{incident.startedAt}</b></span>
            </div>
          </section>

          {top && (
            <section className="card cause">
              <h3>Probable root cause</h3>
              <p className="headline">
                {top.service} <span className="dim">{top.type}</span> <Score c={top} />
              </p>
              <ul className="evidence">
                {top.reason.split('; ').map((r, i) => (
                  <li key={i}>✓ {r}</li>
                ))}
              </ul>
              {incident.trigger && (
                <p className="trigger">
                  Likely trigger: <b>{incident.trigger.service}</b> deployment
                  {' '}(score {(incident.trigger.score * 100).toFixed(1)}%, confidence {incident.trigger.confidence}%)
                </p>
              )}
            </section>
          )}

          <section className="card">
            <h3>Ranked candidates</h3>
            {incident.ranking.length === 0 && (
              <p className="dim">No events in the 60-minute lookback window — nothing to rank.</p>
            )}
            <table>
              <thead>
                <tr>
                  <th>service</th><th>type</th><th>time</th>
                  <th>total</th><th>temp</th><th>dep</th><th>anom</th><th>err</th><th>depl</th>
                </tr>
              </thead>
              <tbody>
                {incident.ranking.map((c) => (
                  <tr key={c.eventId}>
                    <td>{c.service}</td><td>{c.type}</td><td>{c.timestamp}</td>
                    <td><b>{c.score.toFixed(4)}</b></td>
                    <td>{c.temporal}</td><td>{c.dependency}</td><td>{c.anomaly}</td>
                    <td>{c.errorCorr}</td><td>{c.deployment}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>

          <section className="card">
            <h3>Timeline</h3>
            <ol className="timeline">
              {timeline.map((t, i) => (
                <li key={i}>
                  <code>{t.at}</code> <b>{t.kind}</b> {t.label} <span className="dim">[{t.service}]</span>
                </li>
              ))}
            </ol>
          </section>
        </main>
      )}
    </div>
  );
}

import { useState } from 'react';
import { DEMO_INCIDENT, DEMO_MODE, DEMO_TIMELINE } from './demo';
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
      if (DEMO_MODE) {
        if (id !== DEMO_INCIDENT.id) throw new Error(`Not Found — incident ${id} not found`);
        setIncident(DEMO_INCIDENT);
        setTimeline(DEMO_TIMELINE);
      } else {
        const [inc, tl] = await Promise.all([api.getIncident(id), api.timeline(id)]);
        setIncident(inc);
        setTimeline(tl);
      }
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
      if (DEMO_MODE) {
        setIncident(DEMO_INCIDENT);
        setTimeline(DEMO_TIMELINE);
      } else {
        const inc = await seedDemo();
        const tl = await api.timeline(inc.id);
        setIncident(inc);
        setTimeline(tl);
      }
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }

  const top = incident?.ranking[0];

  return (
    <div className="wrap">
      <header className="page-head">
        <div className="eyebrow"><span className="pip" /><span>Incident intelligence · Monochrome</span></div>
        {DEMO_MODE && <p className="dim">Demo dataset — scripted incident, no backend connected.</p>}
        <h1>RootScope</h1>
        <p className="lede">Production incident intelligence — probable cause, with evidence.</p>
      </header>

      <div className="actions">
        <button className="btn-primary" disabled={busy} onClick={demo}>Load PRD demo (payment v4 incident)</button>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (/^\d+$/.test(lookupId.trim())) load(Number(lookupId));
            else setError('Incident id must be a number');
          }}
        >
          <input
            className="input"
            placeholder="incident id"
            value={lookupId}
            onChange={(e) => setLookupId(e.target.value)}
          />
          <button className="btn-secondary" disabled={busy || !lookupId} type="submit">Open</button>
        </form>
      </div>

      {error && <pre className="error">{error}</pre>}

      {incident && (
        <main>
          <section className="card incident">
            <h2>INCIDENT #{incident.id} — {incident.title}</h2>
            <div className="meta">
              <span className="meta-item">Severity <span className={`sev sev-${incident.severity}`}>{incident.severity}</span></span>
              <span className="meta-item">Service <b>{incident.service}</b></span>
              <span className="meta-item">Started <code>{incident.startedAt}</code></span>
            </div>
          </section>

          {top && (
            <section className="card cause">
              <h3>Probable root cause</h3>
              <p className="headline">
                <span className="cause-service">{top.service}</span> <span className="dim">{top.type}</span> <Score c={top} />
              </p>
              <ul className="evidence">
                {top.reason.split('; ').map((r, i) => (
                  <li key={i}>{r}</li>
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
              <p className="empty">No events in the 60-minute lookback window — nothing to rank.</p>
            )}
            {incident.ranking.length > 0 && (
            <div className="table-wrap">
            <table className="geist">
              <thead>
                <tr>
                  <th>service</th><th>type</th><th>time</th>
                  <th>total</th><th>temp</th><th>dep</th><th>anom</th><th>err</th><th>depl</th>
                </tr>
              </thead>
              <tbody>
                {incident.ranking.map((c) => (
                  <tr key={c.eventId}>
                    <td>{c.service}</td><td>{c.type}</td><td className="num">{c.timestamp}</td>
                    <td className="num"><b>{c.score.toFixed(4)}</b></td>
                    <td className="num">{c.temporal}</td><td className="num">{c.dependency}</td><td className="num">{c.anomaly}</td>
                    <td className="num">{c.errorCorr}</td><td className="num">{c.deployment}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
            )}
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

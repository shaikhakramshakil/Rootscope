import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import App from './App';
import { seedDemo, type Incident } from './api';

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>();
  return {
    ...actual,
    api: { getIncident: vi.fn(), timeline: vi.fn(), createIncident: vi.fn(), post: vi.fn() },
    seedDemo: vi.fn(),
  };
});

afterEach(() => cleanup());

const seedDemoMock = vi.mocked(seedDemo);

const incident: Incident = {
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
  ],
  trigger: null,
};

beforeEach(() => {
  vi.clearAllMocks();
});

it('renders the dashboard header', () => {
  render(<App />);
  expect(screen.getByRole('heading', { name: 'RootScope' })).toBeInTheDocument();
  expect(
    screen.getByRole('button', { name: /load prd demo/i }),
  ).toBeInTheDocument();
});

it('loads the demo incident and shows the probable cause', async () => {
  seedDemoMock.mockResolvedValueOnce(incident);
  const { api } = await import('./api');
  vi.mocked(api.timeline).mockResolvedValueOnce([]);

  render(<App />);
  await userEvent.click(screen.getByRole('button', { name: /load prd demo/i }));

  expect(await screen.findByText(/INCIDENT #7/)).toBeInTheDocument();
  const cause = screen.getByText(/Probable root cause/).closest('section');
  expect(cause).not.toBeNull();
  expect(within(cause as HTMLElement).getByText('payment')).toBeInTheDocument();
});

it('shows backend failures instead of crashing', async () => {
  seedDemoMock.mockRejectedValueOnce(new Error('Not Found — incident 9 not found'));
  render(<App />);
  await userEvent.click(screen.getByRole('button', { name: /load prd demo/i }));

  expect(await screen.findByText(/incident 9 not found/)).toBeInTheDocument();
});

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api, seedDemo, type Incident } from './api';

function response(body: unknown, status: number): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: status === 404 ? 'Not Found' : 'Error',
    json: async () => body,
    text: async () => JSON.stringify(body),
  } as Response;
}

const fetchMock = vi.fn();
vi.stubGlobal('fetch', fetchMock);

beforeEach(() => {
  fetchMock.mockReset();
});

describe('api error handling', () => {
  it('surfaces RFC9457 problem details', async () => {
    fetchMock.mockResolvedValueOnce(
      response({ title: 'Not Found', detail: 'incident 42 not found', status: 404 }, 404),
    );
    await expect(api.getIncident(42)).rejects.toThrow('incident 42 not found');
  });

  it('joins field validation errors', async () => {
    fetchMock.mockResolvedValueOnce(
      response(
        {
          title: 'Bad Request',
          detail: 'Request validation failed',
          errors: { severity: 'must match "LOW|MEDIUM|HIGH|CRITICAL"' },
        },
        400,
      ),
    );
    await expect(api.getIncident(1)).rejects.toThrow('severity:');
  });

  it('falls back to status text for empty bodies', async () => {
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 500,
      statusText: 'Server Error',
      json: async () => {
        throw new SyntaxError('empty');
      },
      text: async () => '',
    } as unknown as Response);
    await expect(api.getIncident(1)).rejects.toThrow('500');
  });

  it('treats 202 metric responses as no anomaly', async () => {
    fetchMock.mockResolvedValueOnce({ ok: true, status: 202 } as Response);
    await expect(
      api.post('/metrics', { service: 'api', metric: 'cpu', value: 12 }),
    ).resolves.toBeNull();
  });
});

describe('seedDemo', () => {
  const incident = { id: 7, title: 'Checkout failures', ranking: [] } as unknown as Incident;

  function okEmpty() {
    return { ok: true, status: 200, json: async () => ({}), text: async () => '{}' } as Response;
  }

  it('posts dependencies, deploy, anomalies, then creates the incident', async () => {
    fetchMock
      .mockResolvedValueOnce(okEmpty())
      .mockResolvedValueOnce(okEmpty())
      .mockResolvedValueOnce(okEmpty())
      .mockResolvedValueOnce(okEmpty())
      .mockResolvedValueOnce(okEmpty())
      .mockResolvedValueOnce(okEmpty())
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => incident,
        text: async () => '{}',
      } as Response);

    await expect(seedDemo()).resolves.toEqual(incident);
    expect(fetchMock).toHaveBeenCalledTimes(7);
    expect(fetchMock.mock.calls[3][0]).toContain('/deployments');
    expect(fetchMock.mock.calls[6][0]).toContain('/incidents');
  });
});

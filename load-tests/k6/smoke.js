import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const ITERATIONS = Number(__ENV.ITERATIONS || 5);

export const options = {
  scenarios: {
    application_health_smoke: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: ITERATIONS,
      maxDuration: '1m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
    checks: ['rate>0.99'],
  },
  tags: { phase: '5.2.1', test_type: 'smoke' },
};

export default function () {
  const response = http.get(`${BASE_URL}/actuator/health`, {
    tags: { endpoint: 'actuator_health' },
    timeout: '10s',
  });

  let body = null;
  try {
    body = response.json();
  } catch (_) {
    body = null;
  }

  check(response, {
    'health endpoint returns HTTP 200': (r) => r.status === 200,
    'health response is JSON': (r) => (r.headers['Content-Type'] || '').includes('json'),
    'application health status is UP': () => body !== null && body.status === 'UP',
  });
  sleep(0.2);
}

export function handleSummary(data) {
  return {
    stdout: JSON.stringify(data.metrics, null, 2),
    'load-tests/results/phase-5.2.1-smoke-summary.json': JSON.stringify(data, null, 2),
  };
}

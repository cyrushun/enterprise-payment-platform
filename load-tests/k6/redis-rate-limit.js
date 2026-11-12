import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const EMAIL = __ENV.LOAD_TEST_EMAIL || '';
const PASSWORD = __ENV.LOAD_TEST_PASSWORD || '';
const VUS = Number(__ENV.VUS || 15);
const DURATION = __ENV.DURATION || '20s';
const rejected = new Counter('payment_rate_limit_rejections');

export const options = {
  scenarios: {
    rate_limit_pressure: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_duration: ['p(95)<500'],
  },
  tags: { phase: '5.2.5', test_type: 'redis_rate_limit' },
};

export function setup() {
  if (!EMAIL || !PASSWORD) throw new Error('Set LOAD_TEST_EMAIL and LOAD_TEST_PASSWORD in .env.');
  const login = http.post(`${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } });
  if (login.status !== 200 || !login.json('accessToken')) {
    throw new Error(`Load-test login failed with HTTP ${login.status}.`);
  }
  return { token: login.json('accessToken') };
}

export default function (data) {
  const response = http.get(`${BASE_URL}/api/v1/auth/me`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { endpoint: 'auth_me_rate_limit' },
  });
  if (response.status === 429) rejected.add(1);
  check(response, {
    'rate limiter returns either 200 or expected 429': (r) => r.status === 200 || r.status === 429,
  });
  sleep(0.05);
}

export function handleSummary(data) {
  return {
    stdout: JSON.stringify(data.metrics, null, 2),
    'load-tests/results/phase-5.2.5-redis-rate-limit.json': JSON.stringify(data, null, 2),
  };
}

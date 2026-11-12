import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const EMAIL = __ENV.LOAD_TEST_EMAIL || '';
const PASSWORD = __ENV.LOAD_TEST_PASSWORD || '';
const ACCOUNT_ID = __ENV.LOAD_TEST_ACCOUNT_ID || '';
const VUS = Number(__ENV.VUS || 5);
const DURATION = __ENV.DURATION || '2m';

export const options = {
  scenarios: {
    authenticated_api_baseline: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '30s', target: Math.max(1, Math.ceil(VUS / 2)) },
        { duration: DURATION, target: VUS },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '15s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    checks: ['rate>0.99'],
  },
  tags: { phase: '5.2.2', test_type: 'api_baseline' },
};

export function setup() {
  if (!EMAIL || !PASSWORD || !ACCOUNT_ID) {
    throw new Error('Set LOAD_TEST_EMAIL, LOAD_TEST_PASSWORD and LOAD_TEST_ACCOUNT_ID in .env before running API baseline.');
  }
  const response = http.post(`${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'auth_login' } });
  const ok = check(response, {
    'load-test login returns HTTP 200': (r) => r.status === 200,
    'login response contains accessToken': (r) => {
      try { return Boolean(r.json('accessToken')); } catch (_) { return false; }
    },
  });
  if (!ok) throw new Error(`Load-test login failed with HTTP ${response.status}.`);
  return { token: response.json('accessToken'), accountId: ACCOUNT_ID };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}` };
  const me = http.get(`${BASE_URL}/api/v1/auth/me`, {
    headers, tags: { endpoint: 'auth_me' }, timeout: '10s',
  });
  check(me, { 'GET auth/me returns 200': (r) => r.status === 200 });

  const balance = http.get(`${BASE_URL}/api/v1/accounts/${data.accountId}/balance`, {
    headers, tags: { endpoint: 'account_balance' }, timeout: '10s',
  });
  check(balance, { 'GET account balance returns 200': (r) => r.status === 200 });

  const history = http.get(`${BASE_URL}/api/v1/accounts/${data.accountId}/transfers?page=0&size=20&sort=createdAt,desc`, {
    headers, tags: { endpoint: 'transfer_history' }, timeout: '10s',
  });
  check(history, { 'GET transfer history returns 200': (r) => r.status === 200 });
  sleep(0.2);
}

export function handleSummary(data) {
  return {
    stdout: JSON.stringify(data.metrics, null, 2),
    'load-tests/results/phase-5.2.2-api-baseline.json': JSON.stringify(data, null, 2),
  };
}

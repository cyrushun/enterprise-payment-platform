import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const EMAIL = __ENV.LOAD_TEST_EMAIL || '';
const PASSWORD = __ENV.LOAD_TEST_PASSWORD || '';
const SOURCE = __ENV.LOAD_TEST_SOURCE_ACCOUNT_ID || '';
const DESTINATION = __ENV.LOAD_TEST_DESTINATION_ACCOUNT_ID || '';
const AMOUNT = __ENV.LOAD_TEST_TRANSFER_AMOUNT || '0.01';
const CURRENCY = __ENV.LOAD_TEST_CURRENCY || 'MYR';
const VUS = Number(__ENV.VUS || 3);
const DURATION = __ENV.DURATION || '1m';
const RUN_ID = `${Date.now()}`;

export const options = {
  scenarios: {
    concurrent_transfers: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '20s', target: Math.max(1, Math.ceil(VUS / 2)) },
        { duration: DURATION, target: VUS },
        { duration: '20s', target: 0 },
      ],
      gracefulRampDown: '20s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<750', 'p(99)<1500'],
    checks: ['rate>0.99'],
  },
  tags: { phase: '5.2.3', test_type: 'transfer_load' },
};

export function setup() {
  if (!EMAIL || !PASSWORD || !SOURCE || !DESTINATION) {
    throw new Error('Set LOAD_TEST_EMAIL, LOAD_TEST_PASSWORD, LOAD_TEST_SOURCE_ACCOUNT_ID and LOAD_TEST_DESTINATION_ACCOUNT_ID in .env.');
  }
  if (SOURCE === DESTINATION) throw new Error('Source and destination account IDs must be different.');
  const login = http.post(`${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'auth_login' } });
  if (login.status !== 200 || !login.json('accessToken')) {
    throw new Error(`Load-test login failed with HTTP ${login.status}.`);
  }
  const token = login.json('accessToken');
  const balanceResponse = http.get(`${BASE_URL}/api/v1/accounts/${SOURCE}/balance`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { endpoint: 'transfer_source_preflight' },
  });
  if (balanceResponse.status !== 200) {
    throw new Error(`Source account preflight failed with HTTP ${balanceResponse.status}. Check account ownership and ID.`);
  }
  const availableBalance = Number(balanceResponse.json('balance'));
  if (!Number.isFinite(availableBalance) || availableBalance < Number(AMOUNT)) {
    throw new Error(`Source account balance (${availableBalance}) is below one test transfer amount (${AMOUNT}).`);
  }
  return { token };
}

export default function (data) {
  const idempotencyKey = `phase52-${RUN_ID}-${__VU}-${__ITER}`;
  const payload = {
    sourceAccountId: SOURCE,
    destinationAccountId: DESTINATION,
    amount: AMOUNT,
    currency: CURRENCY,
    description: 'Phase 5.2 controlled performance test',
  };
  const response = http.post(`${BASE_URL}/api/v1/transfers`, JSON.stringify(payload), {
    headers: {
      Authorization: `Bearer ${data.token}`,
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
    tags: { endpoint: 'transfer_create' },
    timeout: '15s',
  });
  check(response, {
    'transfer accepted (HTTP 201)': (r) => r.status === 201,
    'transfer response contains ID': (r) => {
      try { return Boolean(r.json('id')); } catch (_) { return false; }
    },
  });
  sleep(0.2);
}

export function handleSummary(data) {
  return {
    stdout: JSON.stringify(data.metrics, null, 2),
    'load-tests/results/phase-5.2.3-transfer-load.json': JSON.stringify(data, null, 2),
  };
}

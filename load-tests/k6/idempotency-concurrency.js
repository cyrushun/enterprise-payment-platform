import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const EMAIL = __ENV.LOAD_TEST_EMAIL || '';
const PASSWORD = __ENV.LOAD_TEST_PASSWORD || '';
const SOURCE = __ENV.LOAD_TEST_SOURCE_ACCOUNT_ID || '';
const DESTINATION = __ENV.LOAD_TEST_DESTINATION_ACCOUNT_ID || '';
const AMOUNT = __ENV.LOAD_TEST_TRANSFER_AMOUNT || '0.01';
const CURRENCY = __ENV.LOAD_TEST_CURRENCY || 'MYR';
const VUS = Number(__ENV.VUS || 10);
const RUN_ID = `${Date.now()}`;

export const options = {
  scenarios: {
    same_key_concurrency: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: VUS * 2,
      maxDuration: '2m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    checks: ['rate>0.99'],
  },
  tags: { phase: '5.2.3', test_type: 'idempotency_concurrency' },
};

export function setup() {
  if (!EMAIL || !PASSWORD || !SOURCE || !DESTINATION) {
    throw new Error('Set load-test credentials and source/destination account IDs in .env.');
  }
  const login = http.post(`${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } });
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
  const key = `phase52-idem-${RUN_ID}`;
  console.log(`Phase 5.2 idempotency test key: ${key}`);
  return {
    token,
    // All VUs/iterations intentionally share this key and identical payload.
    key,
    payload: {
      sourceAccountId: SOURCE,
      destinationAccountId: DESTINATION,
      amount: AMOUNT,
      currency: CURRENCY,
      description: 'Phase 5.2 idempotency concurrency test',
    },
  };
}

export default function (data) {
  const response = http.post(`${BASE_URL}/api/v1/transfers`, JSON.stringify(data.payload), {
    headers: {
      Authorization: `Bearer ${data.token}`,
      'Content-Type': 'application/json',
      'Idempotency-Key': data.key,
    },
    tags: { endpoint: 'transfer_idempotency' },
    timeout: '20s',
  });
  check(response, {
    'idempotent transfer returns HTTP 201': (r) => r.status === 201,
    'response includes transfer ID': (r) => {
      try { return Boolean(r.json('id')); } catch (_) { return false; }
    },
  });
  sleep(0.05);
}

export function handleSummary(data) {
  return {
    stdout: JSON.stringify(data.metrics, null, 2),
    'load-tests/results/phase-5.2.3-idempotency-concurrency.json': JSON.stringify(data, null, 2),
  };
}

// k6 load test for the full stack through the gateway.
// Start the stack with load-test limits first (see README "Measured results"), then:
// Run:  k6 run -e BASE_URL=http://localhost:8080 load-tests/checkout.js
// Then copy the real numbers (http_reqs rate, http_req_duration p(95)) into README "Measured results".
import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  scenarios: {
    shoppers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    'http_req_failed{name:place-order}': ['rate<0.05'],
    'http_req_failed{name:list-products}': ['rate<0.01'],
    'http_req_duration{name:place-order}': ['p(95)<800'],
    'http_req_duration{name:list-products}': ['p(95)<300'],
  },
};

function json(res) {
  try { return res.json(); } catch (e) { return null; }
}

export function setup() {
  const products = json(http.get(`${BASE}/api/v1/products?size=12`)).data.items;
  return { productIds: products.map((p) => p.id) };
}

function newShopper() {
  const email = `load-${randomString(10)}@payflow.dev`;
  const res = http.post(`${BASE}/api/v1/auth/register`, JSON.stringify({ email, password: 'Password123' }),
    { headers: { 'Content-Type': 'application/json' } });
  const token = json(res)?.data?.accessToken;
  if (!token) {
    return null;
  }
  const auth = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
  topUp(auth);
  topUp(auth);
  topUp(auth);
  return auth;
}

// Max single top-up is 10,000,000 minor units (Rs 1 lakh); shoppers top up again when they run low,
// so the test measures checkout, not "insufficient funds" answers.
function topUp(auth) {
  http.post(`${BASE}/api/v1/wallets/me/top-ups`, JSON.stringify({ amountMinor: 10000000 }),
    { headers: { ...auth, 'Idempotency-Key': `seed-${randomString(16)}` }, tags: { name: 'top-up' } });
}

// Module-level variables live once per virtual user, so each VU registers and tops up exactly once.
let auth = null;

export default function (data) {
  if (!auth) {
    auth = newShopper();
    if (!auth) {
      sleep(1);
      return;
    }
  }

  const list = http.get(`${BASE}/api/v1/products?size=12`, { tags: { name: 'list-products' } });
  check(list, { 'products 200': (r) => r.status === 200 });

  const productId = data.productIds[Math.floor(Math.random() * data.productIds.length)];
  const order = http.post(`${BASE}/api/v1/orders`,
    JSON.stringify({ items: [{ productId, quantity: 1 }] }),
    { headers: { ...auth, 'Idempotency-Key': `ord-${randomString(20)}` }, tags: { name: 'place-order' } });
  check(order, { 'order accepted': (r) => r.status === 202 });
  if (order.status === 422) {
    topUp(auth);
  }

  const id = json(order)?.data?.id;
  if (id) {
    sleep(0.5);
    const got = http.get(`${BASE}/api/v1/orders/${id}`, { headers: auth, tags: { name: 'get-order' } });
    check(got, { 'order readable': (r) => r.status === 200 });
  }
  sleep(1);
}

export function handleSummary(data) {
  return {
    'load-tests/last-summary.json': JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}

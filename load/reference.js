import http from 'k6/http';
import { check, fail } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import exec from 'k6/execution';

const readLatency = new Trend('ledger_read_ms', true);
const writeLatency = new Trend('ledger_write_ms', true);
const reportLatency = new Trend('ledger_report_ms', true);
const unexpected = new Rate('ledger_unexpected_errors');
export const options = {
  scenarios: { workload: { executor: 'constant-arrival-rate', rate: Number(__ENV.RATE || 50), timeUnit: '1s', duration: __ENV.DURATION || '20m', preAllocatedVUs: 30, maxVUs: 100 } },
  thresholds: { ledger_read_ms: ['p(95)<300'], ledger_write_ms: ['p(95)<400'], ledger_report_ms: ['p(95)<3000'], ledger_unexpected_errors: ['rate<0.001'], dropped_iterations: ['count==0'] },
};
const base = __ENV.BASE_URL || 'http://localhost:8080';
const ids = (__ENV.GRANT_IDS || '').split(',').filter(Boolean);
let token;
let tokenExpires = 0;
function headers() {
  if (__ENV.ACCESS_TOKEN) return { Authorization: `Bearer ${__ENV.ACCESS_TOKEN}`, 'Content-Type': 'application/json' };
  if (!token || Date.now() >= tokenExpires) {
    const response = http.post(__ENV.TOKEN_URL || 'http://localhost:8180/realms/grantledger/protocol/openid-connect/token', {
      grant_type: __ENV.CLIENT_CREDENTIALS === 'true' ? 'client_credentials' : 'password',
      client_id: __ENV.CLIENT_ID || 'grantledger-web', client_secret: __ENV.CLIENT_SECRET || 'local-web-only',
      username: __ENV.TEST_USERNAME || 'research-admin', password: __ENV.TEST_PASSWORD || 'local-admin-only',
    }, { tags: { name: 'token-refresh' } });
    if (response.status !== 200) fail('Token acquisition failed');
    token = response.json('access_token'); tokenExpires = Date.now() + (response.json('expires_in') - 30) * 1000;
  }
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}
export function setup() {
  if (ids.length < 20) throw new Error('Supply at least 20 authorized, active grant IDs with TRAVEL funds. Use the synthetic load seed.');
  return { started: Date.now() };
}
export default function(data) {
  const grant = ids[Math.floor(Math.random() * ids.length)];
  const choice = Math.random(); const auth = headers(); let response; let metric;
  if (choice < 0.8) {
    response = http.get(`${base}/api/v1/grants/${grant}/${choice < 0.4 ? 'balance' : 'entries?size=25'}`, { headers: auth, tags: { name: 'grant-read' } }); metric = readLatency;
  } else if (choice < 0.95) {
    const unique = `${Date.now()}-${exec.vu.idInTest}-${exec.scenario.iterationInTest}`;
    auth['Idempotency-Key'] = `00000000-0000-4000-8000-${String(exec.scenario.iterationInTest).padStart(12, '0')}`;
    response = http.post(`${base}/api/v1/grants/${grant}/entries`, JSON.stringify({ category: 'TRAVEL', amount: '0.01', effectiveDate: __ENV.EFFECTIVE_DATE || new Date().toISOString().slice(0,10), externalReference: `LOAD-${unique}`, description: 'Synthetic load test' }), { headers: auth, tags: { name: 'expense-post' } }); metric = writeLatency;
  } else {
    response = http.get(`${base}/api/v1/grants/${grant}/reports/activity.csv?from=${__ENV.EFFECTIVE_DATE || new Date().toISOString().slice(0,10)}`, { headers: auth, tags: { name: 'activity-export' } }); metric = reportLatency;
  }
  const ok = check(response, { 'expected success': response => response.status === 200 || response.status === 201 });
  // The first five minutes warm caches; latency/error thresholds use the measured interval.
  if (Date.now() - data.started >= Number(__ENV.WARMUP_SECONDS || 300) * 1000) { metric.add(response.timings.duration); unexpected.add(!ok); }
}

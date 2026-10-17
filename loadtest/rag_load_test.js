import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const cacheHitRate = new Rate('cache_hits');
const queryDuration = new Trend('query_duration_ms');

export const options = {
  stages: [
    { duration: '30s', target: 5 },   // Ramp-up to 5 users
    { duration: '1m',  target: 20 },  // Stay at 20 users
    { duration: '30s', target: 50 },  // Stress spike to 50 users
    { duration: '30s', target: 0 },   // Ramp-down
  ],
  thresholds: {
    'http_req_duration': ['p(95)<3000'], // 95% of requests must complete below 3s
    'errors': ['rate<0.05'],             // Error rate must stay below 5%
  },
};

const BASE_URL = __ENV.API_URL || 'http://localhost:8080';

// Questions pool for varied querying and semantic cache testing
const QUESTIONS = [
  'What is RagForge and what database does it use for vector search?',
  'How does tenant isolation work in RagForge?',
  'What queuing mechanism is used for document ingestion?',
  'How does RagForge implement semantic caching?',
  'What search algorithms are combined in hybrid search?',
];

export function setup() {
  // Register a dedicated tenant for the load test
  const tenantEmail = `loadtest-${Date.now()}@ragforge.test`;
  const registerPayload = JSON.stringify({
    name: `LoadTestOrg-${Date.now()}`,
    email: tenantEmail,
    password: 'Password123!',
  });

  const res = http.post(`${BASE_URL}/auth/register`, registerPayload, {
    headers: { 'Content-Type': 'application/json' },
  });

  let token = '';
  if (res.status === 201) {
    const body = JSON.parse(res.body);
    token = body.token;
  } else {
    // Fallback: try logging in if already registered
    const loginRes = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
      email: tenantEmail,
      password: 'Password123!',
    }), { headers: { 'Content-Type': 'application/json' } });
    token = JSON.parse(loginRes.body).token;
  }

  return { token };
}

export default function (data) {
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${data.token}`,
  };

  // 1. Health check (10% of traffic)
  if (Math.random() < 0.1) {
    const healthRes = http.get(`${BASE_URL}/health`);
    check(healthRes, {
      'health status is 200': (r) => r.status === 200,
    });
  }

  // 2. Query endpoint
  const question = QUESTIONS[Math.floor(Math.random() * QUESTIONS.length)];
  const queryPayload = JSON.stringify({
    question: question,
    topK: 3,
  });

  const start = Date.now();
  const queryRes = http.post(`${BASE_URL}/query`, queryPayload, { headers });
  const latency = Date.now() - start;
  queryDuration.add(latency);

  const isSuccess = check(queryRes, {
    'query status is 200 or 429': (r) => r.status === 200 || r.status === 429,
  });

  if (!isSuccess) {
    errorRate.add(1);
  } else {
    errorRate.add(0);
    if (queryRes.status === 200) {
      const json = JSON.parse(queryRes.body);
      cacheHitRate.add(json.cacheHit ? 1 : 0);
    }
  }

  // 3. Usage endpoint check periodically (5% of traffic)
  if (Math.random() < 0.05) {
    const usageRes = http.get(`${BASE_URL}/usage`, { headers });
    check(usageRes, {
      'usage status is 200': (r) => r.status === 200,
    });
  }

  sleep(0.5); // 500ms pacing between iterations
}

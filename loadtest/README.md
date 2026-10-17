# RagForge k6 Load Testing (Phase 16)

Performance and stress testing suite for RagForge API using [k6](https://k6.io/).

## Test Scenarios

- **Ramp-up**: 0 to 5 VUs over 30s
- **Sustained Load**: 20 concurrent VUs over 1m
- **Stress Spike**: 50 concurrent VUs over 30s
- **Cooldown**: 50 to 0 VUs over 30s

## Metrics Evaluated

- `http_req_duration`: p95 latency threshold (< 3000ms)
- `errors`: Error rate threshold (< 5%)
- `cache_hits`: Semantic cache hit frequency under concurrent querying
- `query_duration_ms`: End-to-end query processing distribution

## How to Run

### Option 1: Using local k6 binary
```bash
# Point to your local or deployed instance
k6 run -e API_URL=http://localhost:8080 loadtest/rag_load_test.js
```

### Option 2: Using Docker
```bash
docker run --rm -i --network=host grafana/k6 run -e API_URL=http://localhost:8080 - < loadtest/rag_load_test.js
```

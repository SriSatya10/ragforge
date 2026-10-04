# RagForge — Multi-Tenant RAG API Platform

A backend-focused project demonstrating multi-tenant RAG (Retrieval-Augmented Generation) with tenant isolation, async processing, rate limiting, caching, and observability.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| API | Spring Boot 3 (Java 17), Maven, Spring Security (JWT + API key) |
| Database | PostgreSQL 16 + pgvector, Flyway migrations |
| Queue / Cache / Rate Limiter | Redis 7 (Streams, Lua scripts) |
| ML Sidecar | FastAPI, sentence-transformers |
| LLM | Ollama (local, free) |
| Observability | Micrometer + Prometheus + Grafana |
| Deployment | Docker Compose |

## Quick Start (Phase 1)

```bash
# 1. Copy env file and adjust if needed
cp .env.example .env

# 2. Start everything
docker compose up --build

# 3. Verify — should return Postgres + Redis connectivity status
curl http://localhost:8080/health
```

Expected response:
```json
{
  "status": "UP",
  "postgres": {
    "status": "UP",
    "version": "PostgreSQL 16...",
    "pgvector": "installed"
  },
  "redis": {
    "status": "UP",
    "ping": "PONG"
  }
}
```

## Project Structure

```
ragforge/
├── api/                    # Spring Boot backend
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/   # Flyway SQL migrations
├── ml-service/             # (Phase 3+) FastAPI embedding/reranking service
├── eval/                   # (Phase 15) Evaluation scripts
├── loadtest/               # (Phase 16) k6 load test scripts
├── monitoring/             # (Phase 13) Prometheus + Grafana config
├── docker-compose.yml
├── .env.example
└── README.md
```

## Phase Roadmap

- [x] Phase 1: Skeleton + infra + schema
- [ ] Phase 2: Auth (JWT) + tenants + API keys
- [ ] Phase 3: ML sidecar (/embed)
- [ ] Phase 4: Document upload + Redis Stream queue
- [ ] Phase 5: Ingestion worker
- [ ] Phase 6: Vector search + /query
- [ ] Phase 7: Ollama LLM + citations
- [ ] Phase 8: Hybrid search (RRF) + reranker
- [ ] Phase 9: Redis Lua rate limiter
- [ ] Phase 10: Retries + dead-letter queue
- [ ] Phase 11: Semantic cache
- [ ] Phase 12: Tenant isolation + integration tests
- [ ] Phase 13: Metrics (Prometheus, Grafana)
- [ ] Phase 14: Usage logging + /usage
- [ ] Phase 15: Eval script
- [ ] Phase 16: k6 load tests
- [ ] Phase 17: Dockerize fully + README + architecture diagram

# RagForge RAG Evaluation (Phase 15)

Automated evaluation suite for assessing RAG retrieval quality, groundedness, semantic caching efficacy, and latency against a golden dataset.

## Quick Run

```bash
# Set API URL if not running on default localhost:8080
export API_URL=http://localhost:8080

python eval/evaluate.py
```

## Evaluated Metrics

1. **Retrieval Keyword Recall**: Percentage of ground-truth keywords present in the retrieved chunk citations.
2. **Semantic Cache Hit Rate**: Efficacy of query deduplication and cache hits on repeated queries.
3. **End-to-End Latency**: Latency benchmarks for both cold LLM generation queries and warm cache queries.
4. **Tenant Telemetry Validation**: Automatically verifies `/usage` endpoint to ensure queries are accurately tracked in the database.

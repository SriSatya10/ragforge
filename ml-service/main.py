"""
ML Sidecar — lightweight FastAPI service for embeddings and reranking.

This runs as a separate container so:
  1. Python ML libs don't bloat the Java image.
  2. We can scale the ML service independently.
  3. Models are loaded once at startup and kept in memory.
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer, CrossEncoder
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="RagForge ML Sidecar", version="1.0.0")

# ---------------------------------------------------------------------------
# Load models at startup — they stay in memory for the lifetime of the process.
# This avoids reloading on every request (loading takes ~2-5 seconds).
# ---------------------------------------------------------------------------
EMBED_MODEL_NAME = "all-MiniLM-L6-v2"          # 384-dim embeddings
RERANK_MODEL_NAME = "cross-encoder/ms-marco-MiniLM-L-6-v2"  # relevance scoring

embed_model: SentenceTransformer = None
rerank_model: CrossEncoder = None


@app.on_event("startup")
def load_models():
    """Load both models into memory when the server starts."""
    global embed_model, rerank_model
    logger.info(f"Loading embedding model: {EMBED_MODEL_NAME}")
    embed_model = SentenceTransformer(EMBED_MODEL_NAME)
    logger.info(f"Loading reranker model: {RERANK_MODEL_NAME}")
    rerank_model = CrossEncoder(RERANK_MODEL_NAME)
    logger.info("All models loaded successfully")


# ---------------------------------------------------------------------------
# Request / Response schemas
# ---------------------------------------------------------------------------
class EmbedRequest(BaseModel):
    """One or more texts to embed. Batching is more efficient than one-by-one."""
    texts: list[str] = Field(..., min_length=1, description="Texts to embed")


class EmbedResponse(BaseModel):
    embeddings: list[list[float]]
    model: str
    dimensions: int


class RerankRequest(BaseModel):
    """
    Rerank candidate documents against a query.
    The cross-encoder scores each (query, document) pair independently,
    which is more accurate than dot-product similarity but slower — 
    that's why we only rerank the top N candidates, not the whole corpus.
    """
    query: str
    documents: list[str] = Field(..., min_length=1)
    top_k: int = Field(default=5, ge=1, le=100)


class RerankResult(BaseModel):
    index: int          # position in the original documents list
    score: float        # cross-encoder relevance score
    document: str


class RerankResponse(BaseModel):
    results: list[RerankResult]


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------
@app.post("/embed", response_model=EmbedResponse)
def embed(req: EmbedRequest):
    """
    Convert texts to 384-dimensional vectors.
    Supports batching — pass multiple texts for efficient GPU/CPU utilization.
    """
    if embed_model is None:
        raise HTTPException(status_code=503, detail="Model not loaded yet")

    # encode() handles batching internally and returns a numpy array
    vectors = embed_model.encode(req.texts, normalize_embeddings=True)

    return EmbedResponse(
        embeddings=vectors.tolist(),
        model=EMBED_MODEL_NAME,
        dimensions=vectors.shape[1],
    )


@app.post("/rerank", response_model=RerankResponse)
def rerank(req: RerankRequest):
    """
    Score each document against the query and return top_k sorted by relevance.
    Cross-encoder is more accurate than bi-encoder (embedding) similarity
    because it sees query and document together, but it's O(n) per query
    so we only use it on a small candidate set.
    """
    if rerank_model is None:
        raise HTTPException(status_code=503, detail="Model not loaded yet")

    # CrossEncoder expects list of [query, document] pairs
    pairs = [[req.query, doc] for doc in req.documents]
    scores = rerank_model.predict(pairs)

    # Build results with original index, sort by score descending, take top_k
    results = [
        RerankResult(index=i, score=float(scores[i]), document=req.documents[i])
        for i in range(len(req.documents))
    ]
    results.sort(key=lambda r: r.score, reverse=True)
    results = results[: req.top_k]

    return RerankResponse(results=results)


@app.get("/health")
def health():
    """Simple liveness check for Docker healthcheck and debugging."""
    return {
        "status": "UP",
        "embed_model": EMBED_MODEL_NAME if embed_model else "not loaded",
        "rerank_model": RERANK_MODEL_NAME if rerank_model else "not loaded",
    }

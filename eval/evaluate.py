#!/usr/bin/env python3
"""
RagForge Evaluation Script (Phase 15)

Evaluates retrieval quality, answer groundedness, semantic cache efficacy,
and end-to-end latency using the golden dataset.
"""

import json
import os
import sys
import time
from typing import Dict, Any, List
import urllib.request
import urllib.error

BASE_URL = os.getenv("API_URL", "http://localhost:8080")
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATASET_PATH = os.path.join(SCRIPT_DIR, "dataset.json")
SAMPLE_DOC_PATH = os.path.join(SCRIPT_DIR, "sample_doc.txt")


def http_post(url: str, data: dict, headers: dict = None) -> dict:
    if headers is None:
        headers = {}
    headers["Content-Type"] = "application/json"
    req = urllib.request.Request(
        url,
        data=json.dumps(data).encode("utf-8"),
        headers=headers,
        method="POST"
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))


def http_get(url: str, headers: dict = None) -> dict:
    if headers is None:
        headers = {}
    req = urllib.request.Request(url, headers=headers, method="GET")
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))


def upload_multipart(url: str, filepath: str, headers: dict) -> dict:
    boundary = "----WebKitFormBoundaryRagForgeEval"
    filename = os.path.basename(filepath)
    with open(filepath, "rb") as f:
        file_bytes = f.read()

    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        f"Content-Type: text/plain\r\n\r\n"
    ).encode("utf-8") + file_bytes + f"\r\n--{boundary}--\r\n".encode("utf-8")

    headers["Content-Type"] = f"multipart/form-data; boundary={boundary}"
    req = urllib.request.Request(url, data=body, headers=headers, method="POST")
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main():
    print("=" * 60)
    print("        RagForge RAG Pipeline Evaluation (Phase 15)")
    print("=" * 60)
    print(f"Target API: {BASE_URL}")

    # 1. Health check
    try:
        health = http_get(f"{BASE_URL}/health")
        print(f"[1/5] Health Check: {health.get('status', 'OK')}")
    except Exception as e:
        print(f"Error: API is not reachable at {BASE_URL}: {e}")
        sys.exit(1)

    # 2. Authenticate / Register eval tenant
    tenant_email = f"eval-{int(time.time())}@ragforge.test"
    print(f"[2/5] Creating Evaluation Tenant: {tenant_email}")
    reg_payload = {
        "name": f"EvalOrg-{int(time.time())}",
        "email": tenant_email,
        "password": "Password123!"
    }
    reg_res = http_post(f"{BASE_URL}/auth/register", reg_payload)
    token = reg_res["token"]
    auth_headers = {"Authorization": f"Bearer {token}"}

    # 3. Upload knowledge base document
    print(f"[3/5] Ingesting Sample Knowledge Base ({SAMPLE_DOC_PATH})...")
    upload_res = upload_multipart(f"{BASE_URL}/documents", SAMPLE_DOC_PATH, auth_headers.copy())
    doc_id = upload_res["documentId"]
    print(f"      Document ID: {doc_id}, waiting for worker to index chunks...")

    # Poll document status until COMPLETED (or timeout after 60s)
    start_poll = time.time()
    while time.time() - start_poll < 60:
        doc_status = http_get(f"{BASE_URL}/documents/{doc_id}", auth_headers)
        status = doc_status.get("status")
        if status == "COMPLETED":
            print(f"      Document ingestion COMPLETED in {time.time() - start_poll:.1f}s")
            break
        elif status == "FAILED":
            print("      Document ingestion FAILED!")
            sys.exit(1)
        time.sleep(1)

    # 4. Run Golden Dataset Evaluation
    with open(DATASET_PATH, "r", encoding="utf-8") as f:
        dataset = json.load(f)

    print(f"[4/5] Evaluating {len(dataset)} Questions Against RAG Pipeline...")
    results = []
    latencies = []

    for item in dataset:
        qid = item["id"]
        q = item["question"]
        expected_kws = item["expected_keywords"]

        t0 = time.time()
        res1 = http_post(f"{BASE_URL}/query", {"question": q, "topK": 3}, auth_headers)
        elapsed_ms = (time.time() - t0) * 1000
        latencies.append(elapsed_ms)

        answer = res1.get("answer", "")
        citations = res1.get("citations", [])
        cache_hit_1 = res1.get("cacheHit", False)

        # Keyword recall evaluation in citations
        citation_text = " ".join([c.get("content", "").lower() for c in citations])
        matched_kws = [kw for kw in expected_kws if kw.lower() in citation_text or kw.lower() in answer.lower()]
        keyword_recall = len(matched_kws) / len(expected_kws) if expected_kws else 1.0

        # Test Semantic Cache (repeat exact query)
        t_cache = time.time()
        res2 = http_post(f"{BASE_URL}/query", {"question": q, "topK": 3}, auth_headers)
        cache_latency_ms = (time.time() - t_cache) * 1000
        cache_hit_2 = res2.get("cacheHit", False)

        results.append({
            "id": qid,
            "question": q,
            "keyword_recall": keyword_recall,
            "has_citations": len(citations) > 0,
            "latency_ms": elapsed_ms,
            "cache_hit": cache_hit_2,
            "cache_latency_ms": cache_latency_ms
        })

        print(f"  [{qid}] Recall: {keyword_recall * 100:.0f}% | Latency: {elapsed_ms:.0f}ms | Cache Hit: {cache_hit_2} ({cache_latency_ms:.0f}ms)")

    # 5. Check /usage endpoint telemetry
    print("[5/5] Verifying /usage Endpoint Telemetry...")
    usage = http_get(f"{BASE_URL}/usage", auth_headers)
    print(f"      Usage Telemetry: Total Requests = {usage.get('totalRequests')}, "
          f"Cache Hits = {usage.get('totalCacheHits')}, "
          f"Cache Hit Rate = {usage.get('cacheHitRate')}")

    # Summary Statistics
    avg_recall = sum(r["keyword_recall"] for r in results) / len(results)
    avg_latency = sum(latencies) / len(latencies)
    cache_hit_rate = sum(1 for r in results if r["cache_hit"]) / len(results)

    print("\n" + "=" * 60)
    print("                 EVALUATION REPORT SUMMARY")
    print("=" * 60)
    print(f" Total Questions Evaluated:  {len(results)}")
    print(f" Retrieval Keyword Recall:   {avg_recall * 100:.1f}%")
    print(f" Semantic Cache Hit Rate:    {cache_hit_rate * 100:.1f}%")
    print(f" Mean Query Latency:         {avg_latency:.1f} ms")
    print(f" All Queries Grounded:       {all(r['has_citations'] or r['cache_hit'] for r in results)}")
    print("=" * 60)
    print("Evaluation completed successfully.")


if __name__ == "__main__":
    main()

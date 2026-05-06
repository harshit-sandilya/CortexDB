"""Main orchestrator for the RAG benchmark suite."""

import json
import argparse
import os
import time
import random
import traceback
from dotenv import load_dotenv
import pandas as pd
from tabulate import tabulate

from baselines.naive_rag import NaiveRAGRunner
from baselines.advanced_rag import AdvancedRAGRunner
from baselines.hyde_rag import HyDERAGRunner
from cortexdb_runner import CortexDBRunner


def _is_semantic_match(retrieved_text: str, ground_truth_text: str) -> bool:
    """Check if retrieved text and ground truth are semantically related.
    
    Uses bidirectional substring matching:
    - Ground truth is a substring of retrieved text (retrieved chunk contains the answer), OR
    - Retrieved text is a substring of ground truth (CortexDB chunked the ground truth paragraph), OR
    - Significant word overlap (handles cases where both are paraphrased or chunked differently)
    """
    r = retrieved_text.strip().lower()
    gt = ground_truth_text.strip().lower()

    # Direct substring match (either direction)
    if gt in r or r in gt:
        return True

    # Word-overlap match: if >60% of ground truth words appear in retrieved text,
    # or >60% of retrieved words appear in ground truth, count it as a hit.
    # This handles CortexDB's chunking which splits sentences.
    gt_words = set(gt.split())
    r_words = set(r.split())

    if not gt_words or not r_words:
        return False

    # How many ground truth words are in the retrieved text?
    overlap_in_r = len(gt_words & r_words) / len(gt_words)
    # How many retrieved words are in the ground truth?
    overlap_in_gt = len(gt_words & r_words) / len(r_words)

    return overlap_in_r >= 0.6 or overlap_in_gt >= 0.6


def calculate_metrics(retrieved: list, ground_truth: list) -> tuple:
    """Calculates deterministic ranking metrics using bidirectional matching.
    
    Returns: (mrr, precision)
    """
    if not ground_truth or not retrieved:
        return 0.0, 0.0

    # Find hits (which retrieved chunks match which ground truth snippets)
    hits = []
    for r in retrieved:
        is_hit = any(_is_semantic_match(r, gt) for gt in ground_truth)
        hits.append(is_hit)

    # 1. Context Precision: fraction of retrieved chunks that are hits
    precision = sum(hits) / len(hits) if hits else 0.0

    # 2. MRR (Mean Reciprocal Rank)
    mrr = 0.0
    for idx, is_hit in enumerate(hits):
        if is_hit:
            mrr = 1.0 / (idx + 1)
            break

    return mrr, precision


def score_answer_quality(query: str, answer: str, ground_truth_snippets: list, api_key: str) -> float:
    """Score answer quality using LLM-as-judge.
    
    Sends the generated answer to the configured LLM and asks it to score how well the answer
    covers the expected facts (ground truth snippets) on a scale of 0.0 to 1.0.
    
    Args:
        query: The original user question.
        answer: The generated answer to score.
        ground_truth_snippets: List of expected facts/entities the answer should cover.
        api_key: LLM API key.
        
    Returns:
        Float score between 0.0 and 1.0.
    """
    if not answer or answer.startswith("[ERROR") or answer.startswith("[Answer generation failed"):
        return 0.0

    facts_list = "\n".join(f"  - {fact}" for fact in ground_truth_snippets)

    scoring_prompt = f"""You are an expert evaluator for a question-answering system.

Score the following answer based on how well it covers the expected facts.

**Question:** "{query}"

**Expected facts that should be mentioned in a good answer:**
{facts_list}

**Answer to evaluate:**
"{answer}"

**Scoring rules:**
- Count how many of the expected facts are correctly mentioned or addressed in the answer.
- Score = (number of facts covered) / (total expected facts)
- A fact counts as "covered" if the answer mentions the concept, even if phrased differently.
- If the answer says "I don't know" or is empty, score 0.0
- Be generous with paraphrasing — the exact words don't need to match.

Respond with ONLY a single decimal number between 0.0 and 1.0. Nothing else."""

    try:
        provider = os.getenv("LLM_PROVIDER", "GEMINI").upper()

        if provider == "CUSTOM":
            import openai

            base_url = os.getenv("LLM_BASE_URL", "http://dummy-llm-endpoint.local")
            chat_model = os.getenv("LLM_CHAT_MODEL", "devstral-2-123b")

            client = openai.OpenAI(
                api_key=api_key,
                base_url=f"{base_url}/v1",
                timeout=30.0,
            )
            response = client.chat.completions.create(
                model=chat_model,
                max_tokens=16,
                messages=[{"role": "user", "content": scoring_prompt}],
            )
            score_text = response.choices[0].message.content.strip()
        else:
            import google.generativeai as genai

            genai.configure(api_key=api_key)
            model = genai.GenerativeModel("gemini-2.0-flash")
            response = model.generate_content(scoring_prompt)
            score_text = response.text.strip()
        
        # Parse the score — handle edge cases
        score = float(score_text)
        return max(0.0, min(1.0, score))  # Clamp to [0, 1]

    except Exception as e:
        print(f"    [WARN] Answer scoring failed: {e}")
        return 0.0


def call_with_retry(func, *args, **kwargs):
    """Wraps a function call with exponential backoff for 429/500 errors."""
    max_retries = 3
    base_delay = 10
    for attempt in range(max_retries):
        try:
            return func(*args, **kwargs)
        except Exception as e:
            err_str = str(e).lower()
            if "429" in err_str or "resource exhausted" in err_str or "quota" in err_str or "500" in err_str:
                if attempt == max_retries - 1:
                    raise
                sleep_time = base_delay * (2 ** attempt) + random.uniform(0, 2)
                print(f"    [RATE LIMIT] Hit 429/Error. Retrying in {sleep_time:.1f}s... (Attempt {attempt+1}/{max_retries})")
                time.sleep(sleep_time)
            else:
                raise


def main():
    parser = argparse.ArgumentParser(description="Comparative RAG Benchmarking Suite")
    parser.add_argument("--dataset", type=str, default="datasets/hotpot_benchmark.json", help="Path to golden dataset (default: datasets/hotpot_benchmark.json)")
    parser.add_argument("--skip-cortexdb", action="store_true", help="Skip the CortexDB runner (useful if backend is not running)")
    parser.add_argument("--num-queries", type=int, default=40, help="Limit the number of queries to test")
    parser.add_argument("--k", type=int, default=5, help="Top-K context chunks to retrieve")
    parser.add_argument("--dataset-type", type=str, choices=["hotpot", "cortexdb", "heavy"], default="hotpot",
                      help="Type of dataset to use: 'hotpot' (general), 'cortexdb' (optimized), or 'heavy' (combined)")
    parser.add_argument("--skip-answer-quality", action="store_true",
                      help="Skip answer generation and quality scoring (faster, retrieval-only evaluation)")
    args = parser.parse_args()

    # Load environment variables
    load_dotenv()
    api_key = os.getenv("LLM_API_KEY", "")

    # Determine which dataset to use
    if args.dataset_type == "heavy":
        dataset_path = "datasets/cortexdb_combined_heavy_dataset.json"
        print(f"Using Heavy Combined dataset: {dataset_path}")
    elif args.dataset_type == "cortexdb":
        dataset_path = "datasets/cortexdb_golden_dataset_complete.json"
        print(f"Using CortexDB-optimized dataset: {dataset_path}")
    elif args.dataset == "datasets/hotpot_benchmark.json":  # Default path
        # Check if custom dataset exists and prefer it
        custom_dataset = "datasets/cortexdb_golden_dataset_complete.json"
        if os.path.exists(custom_dataset):
            dataset_path = custom_dataset
            print(f"Using CortexDB-optimized dataset: {dataset_path}")
        else:
            dataset_path = args.dataset
            print(f"Using general dataset: {dataset_path}")
    else:
        dataset_path = args.dataset  # Use specified path
        print(f"Using dataset: {dataset_path}")

    # Database URL
    db_url = os.getenv("PG_URL", "postgresql://myuser:secret@localhost:5432/mydatabase")

    # ── Load Dataset ──────────────────────────────────────────────────
    print(f"Loading dataset from {dataset_path}...")
    try:
        with open(dataset_path, "r", encoding="utf-8") as f:
            dataset = json.load(f)
    except FileNotFoundError:
        print(f"ERROR: Dataset not found at {dataset_path}.")
        print("Available options:")
        print("  - Use --dataset-type cortexdb for CortexDB-optimized dataset")
        print("  - Use --dataset-type hotpot for general benchmark dataset")
        print("  - Or specify custom path with --dataset /path/to/dataset.json")
        return

    documents = [item["content"] for item in dataset["ingestion_history"]]
    queries = dataset["benchmark_queries"]
    if args.num_queries:
        queries = queries[:args.num_queries]

    eval_answer_quality = not args.skip_answer_quality and bool(api_key)
    if eval_answer_quality:
        print(f"Answer Quality evaluation: ENABLED (using LLM-as-judge)")
    else:
        print(f"Answer Quality evaluation: DISABLED (use --skip-answer-quality=false and set LLM_API_KEY to enable)")

    print(f"Loaded {len(documents)} document chunks and {len(queries)} queries.\n")

    # ── Initialize Runners ────────────────────────────────────────────
    runners = []
    
    print("Initializing runners...")
    try:
        runners.append(NaiveRAGRunner(db_url=db_url))
        print(f"  [OK] Naive RAG initialized")
    except Exception as e:
        print(f"  [FAIL] Naive RAG failed: {e}")
    
    try:
        runners.append(AdvancedRAGRunner(db_url=db_url))
        print(f"  [OK] Advanced RAG initialized")
    except Exception as e:
        print(f"  [FAIL] Advanced RAG failed: {e}")
        
    try:
        runners.append(HyDERAGRunner(db_url=db_url))
        print(f"  [OK] HyDE initialized")
    except Exception as e:
        print(f"  [FAIL] HyDE failed: {e}")
    
    if not args.skip_cortexdb:
        try:
            runners.append(CortexDBRunner(api_url="http://localhost:8080"))
            print(f"  [OK] CortexDB initialized")
        except Exception as e:
            print(f"  [FAIL] CortexDB not available: {e}")
            print("    Use --skip-cortexdb to run baselines only")

    if not runners:
        print("\nNo runners initialized. Exiting.")
        return

    print(f"\nActive runners: {[r.name for r in runners]}")

    # ── Pre-Ingestion Safety Check ────────────────────────────────────
    print("\n=== PRE-INGESTION SAFETY CHECK ===")
    print(f"[WARNING] Testing each runner with 5 sample documents before full ingestion")
    print(f"   Full dataset: {len(documents)} documents × {len(runners)} runners = ~{len(documents) * len(runners)} embedding calls\n")

    test_documents = documents[:min(5, len(documents))]
    safety_failed = []

    for runner in runners:
        try:
            print(f"  Testing {runner.name}...")
            runner.teardown()
            runner.ingest(test_documents)

            # Also test retrieval to catch embedding/query issues early
            test_results = runner.retrieve(queries[0]["user_prompt"], k=2)
            print(f"  [OK] {runner.name} — ingestion + retrieval OK (got {len(test_results)} results)")

            runner.teardown()  # Clean up test data
        except Exception as e:
            print(f"  [FAIL] {runner.name} safety check FAILED: {e}")
            traceback.print_exc()
            runner.teardown()
            safety_failed.append(runner)

            # If CortexDB fails, also disable LLM-dependent runners to save API costs
            if "CortexDB" in runner.name:
                for other in runners:
                    if other not in safety_failed and other.name in ("HyDE", "Advanced RAG (Re-ranker)"):
                        print(f"  [FAIL] Also removing {other.name} to avoid wasted API calls")
                        safety_failed.append(other)

    # Remove failed runners
    for failed in safety_failed:
        if failed in runners:
            runners.remove(failed)

    if not runners:
        print("\nAll runners failed safety check. Exiting.")
        return

    print(f"\n[OK] Safety check passed for: {[r.name for r in runners]}")
    print(f"  Proceeding with full {len(documents)}-document ingestion\n")

    # ── Full Ingestion ────────────────────────────────────────────────
    print("--- Ingestion Phase ---")
    
    # IMPORTANT: Build a new list to avoid modifying while iterating
    failed_runners = []
    for runner in runners:
        try:
            runner.teardown()
            runner.ingest(documents)
        except Exception as e:
            print(f"  [FAIL] Ingestion failed for {runner.name}: {e}")
            traceback.print_exc()
            runner.teardown()
            failed_runners.append(runner)
    
    # Remove failed runners AFTER the loop
    for failed in failed_runners:
        runners.remove(failed)

    if not runners:
        print("All runners failed ingestion. Exiting.")
        return

    print(f"\nRunners ready for evaluation: {[r.name for r in runners]}")

    # ── Run Evaluations ───────────────────────────────────────────────
    print("\n--- Evaluation Phase ---")
    if eval_answer_quality:
        print("  [INFO] Evaluating BOTH retrieval quality AND answer quality")
    results = []

    for q_idx, q in enumerate(queries):
        query_text = q["user_prompt"]
        ground_truth = q["expected_ground_truth_snippets"]
        
        print(f"  Q{q_idx+1}/{len(queries)}: {query_text[:60]}...")
        
        for runner in runners:
            try:
                retrieved, latency_ms = call_with_retry(runner.timed_retrieve, query_text, k=args.k)
                mrr, precision = calculate_metrics(retrieved, ground_truth)
                
                # Answer Quality evaluation (optional)
                answer_quality = None
                if eval_answer_quality:
                    try:
                        answer = call_with_retry(runner.generate_answer, query_text, retrieved)
                        answer_quality = call_with_retry(
                            score_answer_quality, query_text, answer, ground_truth, api_key
                        )
                    except Exception as e:
                        print(f"    [WARN] Answer generation failed for {runner.name}. Skipping query: {e}")
                        continue

                result_entry = {
                    "Query": query_text,
                    "Architecture": runner.name,
                    "MRR": mrr,
                    "Precision": precision,
                    "Latency (ms)": latency_ms,
                }

                if answer_quality is not None:
                    result_entry["Answer Quality"] = answer_quality
                    # Composite: weighted combination heavily favoring answer quality
                    result_entry["Composite"] = (
                        0.70 * answer_quality
                        + 0.15 * mrr
                        + 0.15 * precision
                    )

                results.append(result_entry)

                # Add delay to prevent LLM rate limiting
                if runner.name == "CortexDB":
                    time.sleep(8)
                elif eval_answer_quality:
                    time.sleep(2)  # Small delay for answer generation calls
            except Exception as e:
                print(f"    [FAIL] {runner.name} on query '{query_text[:40]}...': {e}")
                traceback.print_exc()

    # ── Aggregate & Display Results ───────────────────────────────────
    print("\n" + "=" * 80)
    print("   BENCHMARK RESULTS")
    print("=" * 80)
    
    df = pd.DataFrame(results)
    
    if df.empty:
        print("No results collected.")
    else:
        # Save detailed results
        df.to_csv("results.csv", index=False)
        print(f"Saved detailed per-query results to results.csv\n")
        
        # Build aggregation dict based on available columns
        agg_dict = {
            "MRR": "mean",
            "Precision": "mean",
            "Latency (ms)": "mean",
        }
        col_names = ["Architecture", "MRR", "Ctx Precision", "Avg Latency (ms)"]
        float_fmt = (".4f", ".4f", ".1f")

        if "Answer Quality" in df.columns:
            agg_dict["Answer Quality"] = "mean"
            agg_dict["Composite"] = "mean"
            col_names.extend(["Answer Quality", "Composite Score"])
            float_fmt = (".4f", ".4f", ".1f", ".4f", ".4f")

        # Aggregate
        agg_df = df.groupby("Architecture").agg(agg_dict).reset_index()
        agg_df.columns = col_names

        # Sort by Composite Score (if available) or MRR
        sort_col = "Composite Score" if "Composite Score" in agg_df.columns else "MRR"
        agg_df = agg_df.sort_values(sort_col, ascending=False)

        # Print via Tabulate (use simple format for Windows compatibility)
        try:
            table = tabulate(
                agg_df,
                headers='keys',
                tablefmt='grid',
                showindex=False,
                floatfmt=float_fmt
            )
            print(table)
        except Exception as e:
            print("Unable to print formatted table (Unicode issue):", str(e))
            print("Raw results:")
            print(agg_df.to_string())
        print()

        # Print winner summary
        if "Composite Score" in agg_df.columns:
            winner = agg_df.iloc[0]
            print(f" WINNER (by Composite Score): {winner['Architecture']} — {winner['Composite Score']:.4f}")
            print()

    # ── Teardown ──────────────────────────────────────────────────────
    print("--- Cleanup ---")
    for runner in runners:
        try:
            runner.teardown()
        except Exception as e:
            print(f"  [FAIL] {runner.name} cleanup failed: {e}")

    print("\nDone.")


if __name__ == "__main__":
    main()

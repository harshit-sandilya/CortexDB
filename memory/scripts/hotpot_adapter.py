#!/usr/bin/env python3
"""
HotpotQA → CortexDB Benchmark Adapter

Downloads the HotpotQA Dev Distractor dataset and converts a configurable
number of questions into CortexDB's benchmark.json format.

Usage:
    python hotpot_adapter.py                     # Default: 50 questions
    python hotpot_adapter.py --num-questions 200 # Custom count
    python hotpot_adapter.py --input local.json  # Use a local file instead of downloading

Output:
    ../src/test/resources/hotpot_benchmark.json
"""

import argparse
import json
import os
import sys
import urllib.request

HOTPOTQA_DEV_URL = "http://curtis.ml.cmu.edu/datasets/hotpot/hotpot_dev_distractor_v1.json"
OUTPUT_PATH = os.path.join(
    os.path.dirname(__file__), "..", "src", "test", "resources", "hotpot_benchmark.json"
)


def download_dataset(url: str, cache_path: str) -> list:
    """Download HotpotQA JSON if not cached locally."""
    if os.path.exists(cache_path):
        print(f"[INFO] Using cached dataset: {cache_path}")
        with open(cache_path, "r", encoding="utf-8") as f:
            return json.load(f)

    print(f"[INFO] Downloading HotpotQA dev-distractor from:\n       {url}")
    print(f"       (This is ~600MB, may take a few minutes...)")
    urllib.request.urlretrieve(url, cache_path)
    print(f"[INFO] Download complete. Cached at: {cache_path}")
    with open(cache_path, "r", encoding="utf-8") as f:
        return json.load(f)


def resolve_supporting_facts(context: list, supporting_facts: list) -> list[str]:
    """
    Resolve supporting_facts references into actual sentence strings.

    context:          [[title, [sent0, sent1, ...]], ...]
    supporting_facts: [[title, sent_index], ...]

    Returns a list of sentence strings that are the ground truth evidence.
    """
    # Build a lookup: title -> [sentences]
    title_to_sentences = {}
    for title, sentences in context:
        title_to_sentences[title] = sentences

    resolved = []
    for title, sent_idx in supporting_facts:
        sentences = title_to_sentences.get(title, [])
        if 0 <= sent_idx < len(sentences):
            sentence = sentences[sent_idx].strip()
            if sentence:
                resolved.append(sentence)
    return resolved


def convert_to_benchmark(hotpot_data: list, num_questions: int) -> dict:
    """
    Convert HotpotQA entries into CortexDB benchmark.json format.

    Each HotpotQA entry provides:
      - Multiple context paragraphs (10 paragraphs, only 2 are gold-relevant)
      - A question
      - Supporting facts (the exact sentences that answer the question)

    We flatten ALL context paragraphs into ingestion_history chunks,
    and map the supporting_facts to expected_ground_truth_snippets.
    """
    selected = hotpot_data[:num_questions]

    # Collect ALL unique context paragraphs across all selected questions
    # Each paragraph becomes an ingestion_history entry
    seen_contents = set()
    ingestion_history = []
    chunk_counter = 0

    for entry in selected:
        for title, sentences in entry.get("context", []):
            # Combine all sentences of a paragraph into one chunk
            full_paragraph = f"{title}: {' '.join(sentences)}"
            if full_paragraph not in seen_contents:
                seen_contents.add(full_paragraph)
                ingestion_history.append({
                    "timestamp": f"2026-01-01T{chunk_counter // 3600:02d}:{(chunk_counter % 3600) // 60:02d}:{chunk_counter % 60:02d}Z",
                    "role": "USER",
                    "content": full_paragraph,
                })
                chunk_counter += 1

    # Build benchmark queries
    benchmark_queries = []
    for i, entry in enumerate(selected):
        question = entry["question"]
        answer = entry.get("answer", "")
        level = entry.get("level", "unknown")
        qtype = entry.get("type", "unknown")

        # Resolve supporting facts to actual sentences
        ground_truth_snippets = resolve_supporting_facts(
            entry.get("context", []),
            entry.get("supporting_facts", []),
        )

        if not ground_truth_snippets:
            # Skip questions where we can't resolve supporting facts
            continue

        benchmark_queries.append({
            "query_id": f"hotpot_{i + 1}",
            "user_prompt": question,
            "expected_ground_truth_snippets": ground_truth_snippets,
            "hotpot_answer": answer,
            "hotpot_level": level,
            "hotpot_type": qtype,
            "minimum_acceptable_recall": 0.5,
        })

    benchmark = {
        "dataset_version": "3.0-hotpotqa",
        "description": f"HotpotQA Dev-Distractor benchmark ({len(benchmark_queries)} queries, {len(ingestion_history)} context chunks)",
        "source": "HotpotQA (Yang et al., 2018)",
        "ingestion_history": ingestion_history,
        "benchmark_queries": benchmark_queries,
    }

    return benchmark


def main():
    parser = argparse.ArgumentParser(description="Convert HotpotQA to CortexDB benchmark format")
    parser.add_argument(
        "--num-questions", type=int, default=50,
        help="Number of HotpotQA questions to convert (default: 50)",
    )
    parser.add_argument(
        "--input", type=str, default=None,
        help="Path to a local HotpotQA JSON file (skips download)",
    )
    parser.add_argument(
        "--output", type=str, default=OUTPUT_PATH,
        help=f"Output path for benchmark JSON (default: {OUTPUT_PATH})",
    )
    args = parser.parse_args()

    # Load dataset
    if args.input and os.path.exists(args.input):
        print(f"[INFO] Loading from local file: {args.input}")
        with open(args.input, "r", encoding="utf-8") as f:
            hotpot_data = json.load(f)
    else:
        cache_dir = os.path.dirname(__file__)
        cache_path = os.path.join(cache_dir, "hotpot_dev_distractor_v1.json")
        hotpot_data = download_dataset(HOTPOTQA_DEV_URL, cache_path)

    print(f"[INFO] Total entries in HotpotQA: {len(hotpot_data)}")
    print(f"[INFO] Converting top {args.num_questions} questions...")

    benchmark = convert_to_benchmark(hotpot_data, args.num_questions)

    # Write output
    output_path = os.path.normpath(args.output)
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(benchmark, f, indent=2, ensure_ascii=False)

    print(f"\n[SUCCESS] Benchmark written to: {output_path}")
    print(f"  Ingestion chunks: {len(benchmark['ingestion_history'])}")
    print(f"  Benchmark queries: {len(benchmark['benchmark_queries'])}")
    print(f"\nTo run the benchmark with real embeddings:")
    print(f"  $env:USE_REAL_LLM='true'")
    print(f"  $env:GEMINI_API_KEY='your-key'")
    print(f"  .\\mvnw test -Dtest=RetrievalBenchmarkTest")


if __name__ == "__main__":
    main()

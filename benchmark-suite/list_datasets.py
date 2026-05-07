#!/usr/bin/env python3
"""
List available benchmark datasets and provide usage instructions.
"""

import os
import json
from pathlib import Path

def list_datasets():
    """List all available datasets in the datasets directory."""
    datasets_dir = Path("datasets")

    if not datasets_dir.exists():
        print("❌ No datasets directory found!")
        print("Please ensure datasets are in: benchmark-suite/datasets/")
        return

    print("📚 Available Benchmark Datasets:")
    print("=" * 50)

    dataset_files = list(datasets_dir.glob("*.json"))

    if not dataset_files:
        print("⚠️  No dataset files found in datasets/ directory")
        return

    for i, dataset_file in enumerate(dataset_files, 1):
        print(f"{i}. {dataset_file.name}")

        # Try to load and show dataset info
        try:
            with open(dataset_file, 'r', encoding='utf-8') as f:
                dataset = json.load(f)

                name = dataset.get('dataset_name', 'Unknown')
                description = dataset.get('description', 'No description')
                query_count = dataset.get('total_queries', 'Unknown')

                print(f"   Name: {name}")
                print(f"   Description: {description}")
                print(f"   Queries: {query_count}")
                print()
        except Exception as e:
            print(f"   ⚠️  Could not load dataset info: {e}")
            print()

def show_usage():
    """Show usage instructions."""
    print("\n📖 Usage Instructions:")
    print("=" * 50)
    print()
    print("1. Run general benchmark (HotpotQA dataset):")
    print("   python evaluate.py")
    print()
    print("2. Run CortexDB-optimized benchmark:")
    print("   python evaluate.py --dataset-type cortexdb")
    print()
    print("3. Run with specific dataset:")
    print("   python evaluate.py --dataset datasets/cortexdb_golden_dataset_complete.json")
    print()
    print("4. Run with limited queries (for quick testing):")
    print("   python evaluate.py --num-queries 5")
    print()
    print("5. Skip CortexDB (test other architectures only):")
    print("   python evaluate.py --skip-cortexdb")
    print()

def main():
    print("🔍 CortexDB Benchmark Suite - Dataset Manager")
    print("=" * 50)
    print()

    list_datasets()
    show_usage()

if __name__ == "__main__":
    main()
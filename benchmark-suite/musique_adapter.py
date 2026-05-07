#!/usr/bin/env python3
import json
from datetime import datetime, timedelta
import random

try:
    from datasets import load_dataset
except ImportError:
    print("Error: The 'datasets' library is not installed. Please run 'pip install datasets'.")
    exit(1)

def generate():
    print("Loading bdsaglam/musique dataset from Hugging Face...")
    # Load the validation or train split. MuSiQue train is very large. 
    # The validation set has high quality multi-hop questions.
    dataset = load_dataset("bdsaglam/musique", split="validation")
    
    print(f"Total records in validation set: {len(dataset)}")
    
    # We want 3-hop or 4-hop questions. We can check the length of 'question_decomposition'
    # or 'paragraphs' where is_supporting == True
    hard_questions = []
    
    for record in dataset:
        supporting_paragraphs = [p for p in record["paragraphs"] if p["is_supporting"]]
        # If there are 3 or more supporting paragraphs, it's a 3+ hop question
        if len(supporting_paragraphs) >= 3:
            hard_questions.append(record)
            
        if len(hard_questions) >= 50:
            break
            
    print(f"Collected {len(hard_questions)} hard multi-hop questions.")
    
    cortex_dataset = {
        "dataset_name": "CortexDB_MuSiQue_Benchmark",
        "version": "1.0",
        "description": "A subset of the MuSiQue dataset (3+ hop questions) adapted for CortexDB.",
        "created_date": datetime.now().strftime("%Y-%m-%d"),
        "total_documents": 0,
        "total_queries": len(hard_questions),
        "purpose": "Evaluate complex multi-hop reasoning and graph traversal against real-world QA dataset.",
        "ingestion_history": [],
        "benchmark_queries": []
    }
    
    base_date = datetime(2026, 1, 1)
    
    supporting_paragraphs = {}
    noise_paragraphs = {}
    
    # Process queries and collect unique paragraphs
    for i, record in enumerate(hard_questions):
        query_id = record["id"]
        user_prompt = record["question"]
        
        expected_snippets = []
        for p in record["paragraphs"]:
            title = p["title"]
            text = p["paragraph_text"]
            is_supporting = p["is_supporting"]
            
            # Use title as ground truth for supporting paragraphs
            if is_supporting:
                expected_snippets.append(title)
                
            para_key = f"{title}:::{text}"
            para_dict = {
                "title": title,
                "text": text
            }
            
            if is_supporting:
                supporting_paragraphs[para_key] = para_dict
            else:
                if para_key not in supporting_paragraphs:
                    noise_paragraphs[para_key] = para_dict
                
        # Create benchmark query entry
        cortex_dataset["benchmark_queries"].append({
            "query_id": query_id,
            "user_prompt": user_prompt,
            "expected_ground_truth_snippets": expected_snippets,
            "query_type": "musique_multi_hop",
            "description": f"MuSiQue {len(expected_snippets)}-hop question"
        })
        
    # We want exactly 400 chunks total
    para_list = list(supporting_paragraphs.values())
    print(f"Collected {len(para_list)} supporting paragraphs.")
    
    needed_noise = 400 - len(para_list)
    if needed_noise > 0:
        noise_list = list(noise_paragraphs.values())
        random.shuffle(noise_list)
        para_list.extend(noise_list[:needed_noise])
        print(f"Added {needed_noise} noise paragraphs.")
        
    cortex_dataset["total_documents"] = len(para_list)
    
    # Shuffle paragraphs so related ones aren't ingested sequentially
    random.shuffle(para_list)
    
    # Create ingestion history
    for i, p in enumerate(para_list):
        doc_date = (base_date + timedelta(minutes=i)).strftime("%Y-%m-%dT%H:%M:%SZ")
        # Format paragraph with Title to provide good context for the embedding/graph
        content = f"Title: {p['title']}\n{p['text']}"
        
        cortex_dataset["ingestion_history"].append({
            "timestamp": doc_date,
            "role": "USER",
            "content": content
        })
        
    # Save the dataset
    out_file = 'datasets/cortexdb_musique_benchmark.json'
    with open(out_file, 'w', encoding='utf-8') as f:
        json.dump(cortex_dataset, f, indent=2, ensure_ascii=False)
        
    print(f"MuSiQue dataset adapted successfully at {out_file}")

if __name__ == "__main__":
    generate()

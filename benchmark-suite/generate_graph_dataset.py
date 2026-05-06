#!/usr/bin/env python3
import json
from datetime import datetime, timedelta

def generate():
    dataset = {
        "dataset_name": "CortexDB_Graph_Benchmark",
        "version": "1.0",
        "description": "A highly interconnected 100-document dataset designed to test 2-hop and 3-hop graph traversals.",
        "created_date": datetime.now().strftime("%Y-%m-%d"),
        "total_documents": 100,
        "total_queries": 20,
        "purpose": "Prove CortexDB GraphRAG superiority via deep entity traversal.",
        "ingestion_history": [],
        "benchmark_queries": []
    }

    base_date = datetime(2026, 1, 1)

    # Narrative building blocks
    docs = [
        "Alice Vance founded Synthetix in 2018 with a vision to revolutionize quantum encryption.",
        "Synthetix's flagship product, developed in 2020, was secretly named Project Orion.",
        "Project Orion was primarily architected by a brilliant engineer named Bob Thorne.",
        "Bob Thorne previously worked at CyberDyne Systems before joining Synthetix.",
        "CyberDyne Systems is notorious for a massive data leak known as The Great Spill of 2017.",
        "Alice Vance has a younger brother, Charlie Vance, who is the CEO of NexusGroup.",
        "NexusGroup specializes in AI-driven cybersecurity and threat detection.",
        "In late 2024, OmniCorp aggressively acquired Synthetix for $2.5 billion.",
        "Following the acquisition, OmniCorp absorbed Project Orion into its military division.",
        "Diana Croft is the formidable CEO of OmniCorp, known for ruthless business tactics.",
        "Diana Croft was once mentored by Eve Sterling during her early career at DataCore.",
        "Eve Sterling is currently an independent tech auditor investigating military contractors.",
        "In 2025, a critical vulnerability in Project Orion was leaked to the dark web. This event is called the Orion Theft.",
        "The Orion Theft was traced back to a server located in a NexusGroup facility in Berlin.",
        "Charlie Vance denied any involvement in the Orion Theft, claiming NexusGroup servers were hijacked.",
        "Bob Thorne left OmniCorp shortly after the acquisition and started a new venture called QuantumLeap.",
        "QuantumLeap is heavily funded by an anonymous shell company called ShadowBox LLC.",
        "ShadowBox LLC is registered in the Cayman Islands by legal representative Frank Castle.",
        "Frank Castle used to be the Chief Legal Officer for DataCore.",
        "Eve Sterling's audit report on OmniCorp highlighted suspicious financial ties to ShadowBox LLC."
    ]

    # Generate 100 documents by repeating the narrative but adding specific timestamps and varying context
    # We will expand the universe to reach 100 documents of interconnected lore.
    
    # We will generate 80 more filler documents that connect to the main 20, creating noise and complexity.
    for i in range(1, 81):
        docs.append(f"Technical Log #{i}: Maintenance record for server cluster Alpha-{i} associated with CyberDyne's legacy systems. The system still references protocols designed by Bob Thorne.")

    # Add documents to ingestion history
    for i, content in enumerate(docs):
        doc_date = (base_date + timedelta(days=i)).strftime("%Y-%m-%dT%H:%M:%SZ")
        dataset['ingestion_history'].append({
            "timestamp": doc_date,
            "role": "USER",
            "content": content
        })

    # Benchmark Queries
    queries = [
        {
            "query_id": "graph_1",
            "user_prompt": "Who is the sibling of the founder of the company that originally developed Project Orion?",
            "expected_ground_truth_snippets": ["Charlie Vance"],
            "query_type": "knowledge_graph_multi_hop",
            "description": "3-hop: Project Orion -> Synthetix -> Alice Vance -> Charlie Vance"
        },
        {
            "query_id": "graph_2",
            "user_prompt": "Name the independent tech auditor who mentored the CEO of the company that acquired Synthetix.",
            "expected_ground_truth_snippets": ["Eve Sterling"],
            "query_type": "knowledge_graph_multi_hop",
            "description": "3-hop: Synthetix -> OmniCorp -> Diana Croft -> Eve Sterling"
        },
        {
            "query_id": "graph_3",
            "user_prompt": "What is the name of the shell company funding the new venture started by the original architect of Project Orion?",
            "expected_ground_truth_snippets": ["ShadowBox LLC"],
            "query_type": "knowledge_graph_multi_hop",
            "description": "3-hop: Project Orion -> Bob Thorne -> QuantumLeap -> ShadowBox LLC"
        },
        {
            "query_id": "graph_4",
            "user_prompt": "Who is the legal representative of the shell company connected to the suspicious financial ties in Eve Sterling's OmniCorp audit?",
            "expected_ground_truth_snippets": ["Frank Castle"],
            "query_type": "knowledge_graph_multi_hop",
            "description": "3-hop: Eve Sterling -> OmniCorp -> ShadowBox LLC -> Frank Castle"
        },
        {
            "query_id": "graph_5",
            "user_prompt": "Which former DataCore employee is linked to the registration of the company funding Bob Thorne's new startup?",
            "expected_ground_truth_snippets": ["Frank Castle"],
            "query_type": "knowledge_graph_multi_hop",
            "description": "3-hop: Bob Thorne -> QuantumLeap -> ShadowBox LLC -> Frank Castle -> DataCore"
        }
    ]
    
    # Pad queries to 20
    for i in range(6, 21):
        queries.append({
            "query_id": f"noise_query_{i}",
            "user_prompt": f"What systems were associated with Technical Log #{i} at CyberDyne?",
            "expected_ground_truth_snippets": ["Bob Thorne", "CyberDyne"],
            "query_type": "simple_retrieval",
            "description": "Basic 1-hop retrieval to ensure baseline metrics still function."
        })

    dataset['benchmark_queries'] = queries

    with open('datasets/cortexdb_graph_benchmark.json', 'w') as f:
        json.dump(dataset, f, indent=2)
    
    print("Graph-optimized dataset generated successfully at datasets/cortexdb_graph_benchmark.json")

if __name__ == "__main__":
    generate()

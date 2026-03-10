#Purpose of creating main.py - Proves that our python SDK can actually talk to our Java Backend in a real scenario, outside of unit tests;
"""Example application: Medical Chatbot using CortexDB SDK.

This script demonstrates how to use the CortexDB Python SDK to build a simple
medical chatbot that ingests patient data and answers questions based on it.

Usage:
    python main.py
"""

import os
import sys
import time
from typing import NoReturn

from dotenv import load_dotenv

load_dotenv()

from cortexdb import CortexDB
from cortexdb.exceptions import ConnectionError, CortexDBError
from cortexdb.llm import LLMProvider

# Configuration
API_URL = "http://localhost:8080"
PROVIDER = os.getenv("LLM_PROVIDER", "GEMINI")
API_KEY = os.getenv("LLM_API_KEY") or os.getenv("GEMINI_API_KEY")
if not API_KEY:
    raise ValueError("LLM_API_KEY (or GEMINI_API_KEY) environment variable not set. Check .env file.")
CHAT_MODEL = os.getenv("LLM_CHAT_MODEL", "gemini-2.0-flash")
EMBED_MODEL = os.getenv("LLM_EMBED_MODEL", "gemini-embedding-001")


def print_step(message: str) -> None:
    """Print a styled step message."""
    print(f"\n➤ {message}")


def main() -> None:
    print("🏥 Starting Medical Chatbot (CortexDB Demo)...")

    # 1. Initialize the client
    try:
        db = CortexDB(base_url=API_URL)
        print(f"✅ Connected to CortexDB at {API_URL}")
    except Exception as e:
        print(f"❌ Failed to connect: {e}")
        return

    # 2. Configure LLM (Optional if already configured)
    print_step("Configuring LLM Provider...")
    try:
        # Note: In a real app, you might check if configured first or handle errors
        response = db.setup.configure(
            provider=PROVIDER,
            api_key=API_KEY,  # Ensure this is set!
            chat_model=CHAT_MODEL,
            embed_model=EMBED_MODEL,
        )
        if response.success:
            print(f"✅ LLM Configured: {response.configured_provider}")
        else:
            print(f"⚠️ Setup warning: {response.message}")
    except ConnectionError:
        print("❌ Could not connect to CortexDB server. Is it running?")
        print("   Run: docker compose up --build backend")
        return
    except CortexDBError as e:
        print(f"❌ Setup failed: {e}")
        return

    # 3. Ingest Data (Medical Context)
    print_step("Ingesting Medical Records...")
    patient_id = "patient-101"
    medical_notes = [
        "Patient John Doe (DOB: 1980-05-12) presented with severe headache and light sensitivity.",
        "Examination reveals elevated blood pressure (150/95) and mild fever (100.4°F).",
        "Patient bas history of migraines but reports this feels different, describing it as 'thundering'.",
        "Prescribed lisinopril 10mg daily for hypertension and recommended CT scan to rule out neurological issues.",
        "Patient is allergic to penicillin.",
    ]

    for i, note in enumerate(medical_notes, 1):
        print(f"   Ingesting note {i}/{len(medical_notes)}...", end=" ", flush=True)
        try:
            db.ingest.document(
                uid=patient_id,
                converser="SYSTEM",
                content=note,
                metadata={"type": "medical_record", "priority": "high"},
            )
            print("Done.")
        except CortexDBError as e:
            print(f"Failed: {e}")

    # Allow time for async processing (embedding generation + entity extraction)
    print("   Waiting for processing...", end=" ", flush=True)
    time.sleep(5)  # Give backend time to generate embeddings and extract entities
    print("Ready.")

    # 4. Initialize LLMProvider once (reused across all queries)
    llm = LLMProvider(
        provider=PROVIDER,
        api_key=API_KEY,
        chat_model=CHAT_MODEL,
        embed_model=EMBED_MODEL,
    )

    # 5. Interactive Chat Loop
    print_step("Chat Session Started (Type 'exit' to quit)")
    print(f"You are now chatting about Patient {patient_id}'s records.")

    while True:
        try:
            query = input("\nUser: ").strip()
            if query.lower() in ("exit", "quit"):
                print("Goodbye!")
                break
            if not query:
                continue

            # A. Search Contexts (Retrieval) — broad search to cover all notes
            search_response = db.query.search_contexts(
                query=query,
                limit=10,
                min_relevance=0.3,
            )

            # B. Search Entities (Knowledge Graph)
            entity_response = db.query.search_entities(query=query, limit=5)

            # C. De-duplicate contexts (same note may appear multiple times from re-runs)
            seen_contents = set()
            unique_results = []
            for res in search_response.results:
                if res.content not in seen_contents:
                    seen_contents.add(res.content)
                    unique_results.append(res)

            # D. Display retrieval results
            print(f"   Found {len(unique_results)} unique relevant notes.")
            for res in unique_results:
                print(f"   - [Context] {res.content[:60]}... (Score: {res.score:.2f})")

            for res in entity_response.results:
                print(f"   - [Entity] {res.content} ({res.type})")

            # E. Build prompt with BOTH context chunks AND entity information
            context_str = "\n".join([f"- {r.content}" for r in unique_results])
            entity_str = "\n".join(
                [f"- {r.content} ({r.type})" for r in entity_response.results if r.content]
            )

            prompt = (
                f"You are a helpful medical assistant. Answer the user's question based ONLY on the following information.\n\n"
                f"## Patient Notes:\n{context_str}\n\n"
            )
            if entity_str:
                prompt += f"## Extracted Entities:\n{entity_str}\n\n"
            prompt += f"Question: {query}\nAnswer:"

            # F. Generate answer
            print("\n   🤖 Generating answer...", end=" ", flush=True)
            try:
                answer = llm.call_llm(prompt)
                print("Done.")
                print(f"\nBot: {answer}\n")
            except Exception as e:
                print(f"Failed: {e}")

        except KeyboardInterrupt:
            print("\nGoodbye!")
            break
        except CortexDBError as e:
            print(f"❌ Error: {e}")


if __name__ == "__main__":
    main()

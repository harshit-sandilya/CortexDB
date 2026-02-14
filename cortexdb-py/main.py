#Purpose of creating main.py - Proves that our python SDK can actually talk to our Java Backend in a real scenario, outside of unit tests;
"""Example application: Medical Chatbot using CortexDB SDK.

This script demonstrates how to use the CortexDB Python SDK to build a simple
medical chatbot that ingests patient data and answers questions based on it.

Usage:
    python main.py
"""

import sys
import time
from typing import NoReturn

from cortexdb import CortexDB
from cortexdb.exceptions import ConnectionError, CortexDBError
from cortexdb.llm import LLMProvider

# Configuration
API_URL = "http://localhost:8080"
PROVIDER = "GEMINI"  # Or "OPENAI", "AZURE"
API_KEY = "AIzaSyD0u8-6xTbfUJLK1ZzUL0SGYCSFeqGtRmU"  # Replace with actual key or use env var
CHAT_MODEL = "gemini-2.0-flash"
EMBED_MODEL = "gemini-embedding-001"


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

    # Allow some time for async processing (embedding generation)
    print("   Waiting for processing...", end=" ", flush=True)
    time.sleep(2)  # Simple wait; in production, you might poll status
    print("Ready.")

    # 4. Interactive Chat Loop
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

            # A. Search Contexts (Retrieval)
            # We search for relevant medical notes based on the query
            search_response = db.query.search_contexts(
                query=query,
                limit=3,
                min_relevance=0.6,
            )

            # B. Search Entities (Knowledge Graph)
            # We also check for extracted entities (e.g., medications, symptoms)
            entity_response = db.query.search_entities(query=query, limit=2)

            # C. Synthesize Answer (using RAG)
            # In a full system, the backend might do this via /api/chat.
            # Here, we demonstrate doing it client-side or inspecting the retrieval.
            
            print(f"   Found {len(search_response.results)} relevant notes.")
            for res in search_response.results:
                print(f"   - [Context] {res.content[:60]}... (Score: {res.score:.2f})")

            for res in entity_response.results:
                print(f"   - [Entity] {res.content} ({res.type})")

            # Generate Answer using LLMProvider
            print("\n   🤖 Generating answer...", end=" ", flush=True)
            llm = LLMProvider(
                provider=PROVIDER,
                api_key=API_KEY,
                chat_model=CHAT_MODEL,
                embed_model=EMBED_MODEL
            )
            
            context_str = "\n".join([f"- {r.content}" for r in search_response.results])
            prompt = (
                f"You are a helpful medical assistant. Answer the user's question based ONLY on the following context:\n\n"
                f"{context_str}\n\n"
                f"Question: {query}\n"
                f"Answer:"
            )
            
            try:
                answer = llm.call_llm(prompt)
                print("Done.")
                print(f"\nExample Bot: {answer}\n")
            except Exception as e:
                print(f"Failed: {e}")
            
        except KeyboardInterrupt:
            print("\nGoodbye!")
            break
        except CortexDBError as e:
            print(f"❌ Error: {e}")


if __name__ == "__main__":
    main()

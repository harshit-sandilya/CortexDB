/// <reference types="node" />
/**
 * Example application: Medical Chatbot using the CortexDB JS SDK.
 *
 * Proves that cortexdb-js can talk to the Java backend in a real scenario,
 * outside of unit tests — a 1:1 port of cortexdb-py/main.py.
 *
 * Usage:
 *   npx tsx main.ts
 */

import "dotenv/config";
import * as readline from "node:readline/promises";
import { stdin as input, stdout as output } from "node:process";
import { CortexDB } from "./src/index.js";

// ─── Configuration ────────────────────────────────────────────────────────────

const API_URL = process.env["CORTEXDB_URL"] ?? "http://localhost:8080";
const PROVIDER = process.env["LLM_PROVIDER"] ?? "GEMINI";
const API_KEY = process.env["LLM_API_KEY"] ?? process.env["GEMINI_API_KEY"];
const CHAT_MODEL = process.env["LLM_CHAT_MODEL"] ?? process.env["GEMINI_CHAT_MODEL"] ?? "gemini-2.0-flash";
const EMBED_MODEL = process.env["LLM_EMBED_MODEL"] ?? process.env["GEMINI_EMBED_MODEL"] ?? "gemini-embedding-001";

if (!API_KEY) {
    console.error("❌ LLM_API_KEY (or GEMINI_API_KEY) environment variable not set. Check your .env file.");
    process.exit(1);
}

// ─── Tiny LLM helper ───────────────────────────────────────────────────────────

/** Call the LLM REST endpoint and return the text reply. */
async function callLLM(prompt: string): Promise<string> {
    const isGemini = PROVIDER.toUpperCase() === "GEMINI";

    if (isGemini) {
        const url = `https://generativelanguage.googleapis.com/v1beta/models/${CHAT_MODEL}:generateContent?key=${API_KEY}`;
        const res = await fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ contents: [{ parts: [{ text: prompt }] }] }),
            signal: AbortSignal.timeout(30_000),
        });

        if (!res.ok) throw new Error(`Gemini API error ${res.status}: ${await res.text().catch(() => "")}`);
        const data: any = await res.json();
        return data?.candidates?.[0]?.content?.parts?.[0]?.text ?? "(no response)";
    } else {
        // Simple fallback for OpenAI-compatible APIs in the demo script
        let baseUrl = "https://api.openai.com/v1";
        if (PROVIDER.toUpperCase() === "OPENROUTER") baseUrl = "https://openrouter.ai/api/v1";

        const res = await fetch(`${baseUrl}/chat/completions`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${API_KEY}`
            },
            body: JSON.stringify({
                model: CHAT_MODEL,
                messages: [{ role: "user", content: prompt }]
            }),
            signal: AbortSignal.timeout(30_000),
        });

        if (!res.ok) throw new Error(`LLM API error ${res.status}: ${await res.text().catch(() => "")}`);
        const data: any = await res.json();
        return data?.choices?.[0]?.message?.content ?? "(no response)";
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function printStep(msg: string) {
    console.log(`\n➤ ${msg}`);
}

// ─── Main ─────────────────────────────────────────────────────────────────────

async function main() {
    console.log("🏥 Starting Medical Chatbot (CortexDB JS Demo)...");

    // 1. Initialise client
    let db: CortexDB;
    try {
        db = new CortexDB(API_URL);
        console.log(`✅ Connected to CortexDB at ${API_URL}`);
    } catch (e: any) {
        console.error(`❌ Failed to connect: ${e.message}`);
        return;
    }

    // 2. Configure LLM
    printStep("Configuring LLM Provider...");
    try {
        const resp = await db.setup.configure(PROVIDER, CHAT_MODEL, EMBED_MODEL, API_KEY);
        if (resp?.success) {
            console.log(`✅ LLM Configured: ${resp.configuredProvider}`);
        } else {
            console.warn(`⚠️  Setup warning: ${resp?.message}`);
        }
    } catch (e: any) {
        if (e.message?.includes("fetch")) {
            console.error("❌ Could not connect to CortexDB server. Is it running?");
            console.error("   Run: docker compose up --build backend");
            return;
        }
        console.error(`❌ Setup failed: ${e.message}`);
        return;
    }

    // 3. Ingest medical records
    printStep("Ingesting Medical Records...");
    const patientId = "patient-101";
    const medicalNotes = [
        "Patient John Doe (DOB: 1980-05-12) presented with severe headache and light sensitivity.",
        "Examination reveals elevated blood pressure (150/95) and mild fever (100.4°F).",
        "Patient has history of migraines but reports this feels different, describing it as 'thundering'.",
        "Prescribed lisinopril 10mg daily for hypertension and recommended CT scan to rule out neurological issues.",
        "Patient is allergic to penicillin.",
    ];

    for (let i = 0; i < medicalNotes.length; i++) {
        process.stdout.write(`   Ingesting note ${i + 1}/${medicalNotes.length}... `);
        try {
            await db.ingest.document(
                patientId,
                "SYSTEM",
                medicalNotes[i],
                { type: "medical_record", priority: "high" },
            );
            console.log("Done.");
        } catch (e: any) {
            console.log(`Failed: ${e.message}`);
        }
    }

    // Allow time for async embedding generation + entity extraction
    process.stdout.write("   Waiting for processing... ");
    await new Promise(r => setTimeout(r, 5000));
    console.log("Ready.");

    // 4. Interactive chat loop
    printStep("Chat Session Started (Type 'exit' to quit)");
    console.log(`You are now chatting about Patient ${patientId}'s records.`);

    const rl = readline.createInterface({ input, output });

    while (true) {
        let query: string;
        try {
            query = (await rl.question("\nUser: ")).trim();
        } catch {
            // readline closed (Ctrl+C / EOF)
            console.log("\nGoodbye!");
            break;
        }

        if (!query) continue;
        if (["exit", "quit"].includes(query.toLowerCase())) {
            console.log("Goodbye!");
            break;
        }

        try {
            // A. Retrieve relevant context chunks
            const searchResponse = await db.query.searchContexts(query, 10, 0.3);

            // B. Search knowledge-graph entities
            const entityResponse = await db.query.searchEntities(query, 5);

            // C. De-duplicate contexts (same note may appear from re-runs)
            const seenContents = new Set<string>();
            const uniqueResults = searchResponse.results.filter(r => {
                if (seenContents.has(r.content)) return false;
                seenContents.add(r.content);
                return true;
            });

            // D. Display retrieval results
            console.log(`   Found ${uniqueResults.length} unique relevant notes.`);
            for (const res of uniqueResults) {
                console.log(`   - [Context] ${res.content.slice(0, 60)}... (Score: ${res.score.toFixed(2)})`);
            }
            for (const res of entityResponse.results) {
                if (res.content) {
                    console.log(`   - [Entity] ${res.content} (${res.type})`);
                }
            }

            // E. Build prompt
            const contextStr = uniqueResults.map(r => `- ${r.content}`).join("\n");
            const entityStr = entityResponse.results
                .filter(r => r.content)
                .map(r => `- ${r.content} (${r.type})`)
                .join("\n");

            let prompt =
                `You are a helpful medical assistant. Answer the user's question based ONLY on the following information.\n\n` +
                `## Patient Notes:\n${contextStr}\n\n`;
            if (entityStr) {
                prompt += `## Extracted Entities:\n${entityStr}\n\n`;
            }
            prompt += `Question: ${query}\nAnswer:`;

            // F. Generate answer via LLM
            process.stdout.write("\n   🤖 Generating answer... ");
            try {
                const answer = await callLLM(prompt);
                console.log("Done.");
                console.log(`\nBot: ${answer}\n`);
            } catch (e: any) {
                console.log(`Failed: ${e.message}`);
            }

        } catch (e: any) {
            console.error(`❌ Error: ${e.message}`);
        }
    }

    rl.close();
}

main();

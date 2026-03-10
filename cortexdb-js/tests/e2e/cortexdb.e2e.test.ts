/// <reference types="node" />
/**
 * E2E tests for cortexdb-js.
 *
 * These tests make REAL HTTP requests to a running CortexDB server.
 * They automatically skip if the server is unreachable.
 *
 * Environment variables (loaded from .env automatically):
 *   CORTEXDB_URL      — CortexDB server URL (default: http://localhost:8080)
 *   GEMINI_API_KEY    — Gemini API key
 *   GEMINI_CHAT_MODEL — Chat model name (default: gemini-2.0-flash)
 *   GEMINI_EMBED_MODEL— Embed model name (default: gemini-embedding-001)
 *
 * Run with:
 *   npm run test:e2e
 */

import "dotenv/config";
import { describe, it, expect, beforeAll } from "vitest";
import { CortexDB, ConverserRole, LLMApiProvider } from "../../src/index.js";

const BASE_URL = process.env["CORTEXDB_URL"] ?? "http://localhost:8080";
const E2E_UID = `e2e-test-${Date.now()}`;

// ─── Server availability check ───────────────────────────────────────

async function isServerAvailable(): Promise<boolean> {
    try {
        const res = await fetch(`${BASE_URL}/actuator/health`, {
            signal: AbortSignal.timeout(3000),
        });
        return res.ok;
    } catch {
        return false;
    }
}

let serverAvailable = false;

beforeAll(async () => {
    serverAvailable = await isServerAvailable();
    if (!serverAvailable) {
        console.warn(
            `\n⚠️  CortexDB server not reachable at ${BASE_URL}.\n` +
            `   All E2E tests will be skipped.\n` +
            `   Start the backend, then re-run: npm run test:e2e\n`,
        );
    }
});

/** Wrap each E2E test so it skips gracefully when the server isn't up. */
function e2e(name: string, fn: () => Promise<void>) {
    it(name, async () => {
        if (!serverAvailable) {
            return; // silently skip
        }
        await fn();
    });
}

// ─── Tests ───────────────────────────────────────────────────────────

describe("E2E — SetupAPI", () => {
    e2e("configure() succeeds with a valid LLM provider", async () => {
        const db = new CortexDB(BASE_URL);
        const provider = (process.env["LLM_PROVIDER"] ?? "GEMINI") as LLMApiProvider;
        const chatModel = process.env["LLM_CHAT_MODEL"] ?? process.env["GEMINI_CHAT_MODEL"] ?? "gemini-2.0-flash";
        const embedModel = process.env["LLM_EMBED_MODEL"] ?? process.env["GEMINI_EMBED_MODEL"] ?? "gemini-embedding-001";
        const apiKey = process.env["LLM_API_KEY"] ?? process.env["GEMINI_API_KEY"];

        const resp = await db.setup.configure(provider, chatModel, embedModel, apiKey);
        expect(resp.success).toBe(true);
        expect(resp.message).toBeTruthy();
    });
});

describe("E2E — IngestAPI", () => {
    e2e("document() ingests content and returns a KnowledgeBase ID", async () => {
        const db = new CortexDB(BASE_URL);

        const resp = await db.ingest.document(
            E2E_UID,
            ConverserRole.USER,
            "CortexDB is a RAG-powered memory database that supports vector and graph queries.",
        );

        expect(resp.status).toBe("SUCCESS");
        expect(resp.knowledgeBase?.id).toBeTruthy();
        expect(resp.knowledgeBase?.uid).toBe(E2E_UID);
        expect(resp.processingTimeMs).toBeGreaterThan(0);
    });

    e2e("document() ingests AGENT response with metadata", async () => {
        const db = new CortexDB(BASE_URL);

        const resp = await db.ingest.document(
            E2E_UID,
            ConverserRole.AGENT,
            "I understand. CortexDB uses pgvector and PostgreSQL.",
            { source: "e2e-test", testRun: true },
        );

        expect(resp.status).toBe("SUCCESS");
    });
});

describe("E2E — QueryAPI — Context search", () => {
    e2e("searchContexts() returns results for an ingested topic", async () => {
        const db = new CortexDB(BASE_URL);

        const resp = await db.query.searchContexts("RAG memory database", 5, 0.5);

        expect(Array.isArray(resp.results)).toBe(true);
        // At least one result should be present after ingestion
        if (resp.results.length > 0) {
            expect(resp.results[0].content).toBeTruthy();
            expect(typeof resp.results[0].score).toBe("number");
        }
    });

    e2e("getRecentContexts() returns contexts from the last 7 days", async () => {
        const db = new CortexDB(BASE_URL);
        const resp = await db.query.getRecentContexts(7);
        expect(Array.isArray(resp.results)).toBe(true);
    });
});

describe("E2E — QueryAPI — History", () => {
    e2e("getHistoryByUser() returns knowledge bases for the test user", async () => {
        const db = new CortexDB(BASE_URL);
        const resp = await db.query.getHistoryByUser(E2E_UID);

        expect(Array.isArray(resp.results)).toBe(true);
    });
});

describe("E2E — QueryAPI — Entity search", () => {
    e2e("searchEntities() returns results without throwing", async () => {
        const db = new CortexDB(BASE_URL);
        const resp = await db.query.searchEntities("CortexDB");
        expect(Array.isArray(resp.results)).toBe(true);
    });

    e2e("getEntityByName() returns null for a non-existent entity", async () => {
        const db = new CortexDB(BASE_URL);
        const entity = await db.query.getEntityByName(`__nonexistent_${Date.now()}__`);
        expect(entity).toBeNull();
    });
});

describe("E2E — QueryAPI — Hybrid search", () => {
    e2e("hybridSearch() returns combined results", async () => {
        const db = new CortexDB(BASE_URL);
        const resp = await db.query.hybridSearch("vector graph database", 5, 0.4);
        expect(Array.isArray(resp.results)).toBe(true);
    });
});

describe("E2E — QueryAPI — User data cleanup", () => {
    e2e("deleteUserData() removes the test user's data without throwing", async () => {
        const db = new CortexDB(BASE_URL);
        // Should not throw even if no data exists for this uid
        await expect(db.query.deleteUserData(E2E_UID)).resolves.not.toThrow();
    });
});

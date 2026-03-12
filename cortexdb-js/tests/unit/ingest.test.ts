/**
 * Unit tests for IngestAPI.
 * Equivalent to cortexdb-py/tests/test_ingest.py
 */

import { describe, it, expect, afterEach } from "vitest";
import { CortexDB, ConverserRole } from "../../src/index.js";
import { mockFetch, restoreFetch } from "./helpers.js";

afterEach(() => restoreFetch());

describe("IngestAPI", () => {
    it("document() sends correct payload", async () => {
        let capturedBody: any;

        mockFetch((req) => {
            expect(req.url).toBe("/api/v1/memory/ingest/document");
            expect(req.method).toBe("POST");
            capturedBody = req.body;
            return {
                status: 200,
                body: {
                    status: "SUCCESS",
                    message: "Document ingested successfully",
                    processingTimeMs: 250,
                    embeddingTimeMs: 80,
                    knowledgeBase: {
                        id: "550e8400-e29b-41d4-a716-446655440000",
                        uid: "user-1",
                        content: "Hello world",
                    },
                },
            };
        });

        const db = new CortexDB("http://testserver");
        const resp = await db.ingest.document("user-1", "Test Title", "Hello world");

        // Response parsed correctly
        expect(resp.status).toBe("SUCCESS");
        expect(resp.processingTimeMs).toBe(250);
        expect(resp.embeddingTimeMs).toBe(80);
        expect(resp.knowledgeBase?.uid).toBe("user-1");
        expect(resp.knowledgeBase?.content).toBe("Hello world");

        // Request body is correct
        expect(capturedBody.uid).toBe("user-1");
        expect(capturedBody.documentTitle).toBe("Test Title");
        expect(capturedBody.documentText).toBe("Hello world");
    });

    it("prompt() sends correct payload", async () => {
        let capturedBody: any;

        mockFetch((req) => {
            expect(req.url).toBe("/api/v1/memory/ingest/prompt");
            expect(req.method).toBe("POST");
            capturedBody = req.body;
            return {
                status: 200,
                body: {
                    status: "SUCCESS",
                    message: "Prompt ingested successfully",
                    processingTimeMs: 250,
                    embeddingTimeMs: 80,
                    knowledgeBase: {
                        id: "550e8400-e29b-41d4-a716-446655440000",
                        uid: "user-1",
                        content: "Hello world",
                    },
                },
            };
        });

        const db = new CortexDB("http://testserver");
        const resp = await db.ingest.prompt("user-1", "USER", "Hello world");

        // Response parsed correctly
        expect(resp.status).toBe("SUCCESS");
        expect(resp.processingTimeMs).toBe(250);
        expect(resp.embeddingTimeMs).toBe(80);
        expect(resp.knowledgeBase?.uid).toBe("user-1");
        expect(resp.knowledgeBase?.content).toBe("Hello world");

        // Request body is correct
        expect(capturedBody.uid).toBe("user-1");
        expect(capturedBody.converser).toBe("USER");
        expect(capturedBody.text).toBe("Hello world");
    });

    it("document() sends metadata when provided", async () => {
        let capturedBody: any;

        mockFetch((req) => {
            capturedBody = req.body;
            return { status: 200, body: { status: "SUCCESS", message: "OK" } };
        });

        const db = new CortexDB("http://testserver");
        await db.ingest.prompt("user-1", "AGENT", "Some content", {
            source: "test",
            priority: 1,
        });

        expect(capturedBody.metadata?.source).toBe("test");
        expect(capturedBody.metadata?.priority).toBe(1);
    });

    it("document() omits metadata from body when not provided", async () => {
        let capturedBody: any;

        mockFetch((req) => {
            capturedBody = req.body;
            return { status: 200, body: { status: "SUCCESS" } };
        });

        const db = new CortexDB("http://testserver");
        await db.ingest.prompt("user-1", "USER", "Content");

        expect(capturedBody.metadata).toBeUndefined();
    });

    it("document() accepts ConverserRole enum", async () => {
        let capturedBody: any;

        mockFetch((req) => {
            capturedBody = req.body;
            return { status: 200, body: { status: "SUCCESS" } };
        });

        const db = new CortexDB("http://testserver");
        await db.ingest.prompt("user-1", ConverserRole.SYSTEM, "System message");

        expect(capturedBody.converser).toBe("SYSTEM");
    });

    it("document() accepts lowercase converser string and uppercases it", async () => {
        let capturedBody: any;

        mockFetch((req) => {
            capturedBody = req.body;
            return { status: 200, body: { status: "SUCCESS" } };
        });

        const db = new CortexDB("http://testserver");
        await db.ingest.prompt("user-1", "agent", "Content");

        expect(capturedBody.converser).toBe("AGENT");
    });

    it("document() throws on server error", async () => {
        mockFetch(() => ({ status: 400, body: { error: "Bad request" } }));

        const db = new CortexDB("http://testserver");
        await expect(
            db.ingest.prompt("user-1", "USER", ""),
        ).rejects.toThrow("CortexDB HTTP Error: 400");
    });
});

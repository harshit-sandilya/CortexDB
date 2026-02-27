/**
 * Unit tests for SetupAPI.
 * Equivalent to cortexdb-py/tests/test_setup.py
 */

import { describe, it, expect, afterEach } from "vitest";
import { CortexDB, LLMApiProvider } from "../../src/index.js";
import { mockFetch, restoreFetch } from "./helpers.js";

afterEach(() => restoreFetch());

describe("SetupAPI", () => {
    it("configure() sends correct payload with all fields", async () => {
        let capturedBody: any;

        mockFetch((req) => {
            expect(req.url).toBe("/api/setup");
            expect(req.method).toBe("POST");
            capturedBody = req.body;
            return {
                status: 200,
                body: {
                    message: "LLM configured successfully",
                    success: true,
                    configuredProvider: "GEMINI",
                    configuredChatModel: "gemini-2.0-flash",
                    configuredEmbedModel: "gemini-embedding-001",
                },
            };
        });

        const db = new CortexDB("http://testserver");
        const resp = await db.setup.configure(
            "GEMINI",
            "gemini-2.0-flash",
            "gemini-embedding-001",
            "test-key",
        );

        // Response parsed correctly
        expect(resp.success).toBe(true);
        expect(resp.configuredProvider).toBe("GEMINI");
        expect(resp.configuredChatModel).toBe("gemini-2.0-flash");
        expect(resp.configuredEmbedModel).toBe("gemini-embedding-001");

        // Request body is correct camelCase (matches Java backend)
        expect(capturedBody.provider).toBe("GEMINI");
        expect(capturedBody.apiKey).toBe("test-key");
        expect(capturedBody.chatModelName).toBe("gemini-2.0-flash");
        expect(capturedBody.embedModelName).toBe("gemini-embedding-001");
    });

    it("configure() sends baseUrl when provided", async () => {
        let capturedBody: any;

        mockFetch((req) => {
            capturedBody = req.body;
            return {
                status: 200,
                body: { message: "OK", success: true, baseUrl: "https://custom.openai.com" },
            };
        });

        const db = new CortexDB("http://testserver");
        const resp = await db.setup.configure(
            "OPENAI",
            "gpt-4",
            "text-embedding-ada-002",
            "sk-test",
            "https://custom.openai.com",
        );

        expect(resp.success).toBe(true);
        expect(capturedBody.baseUrl).toBe("https://custom.openai.com");
    });

    it("configure() omits apiKey from body when not provided", async () => {
        let capturedBody: any;

        mockFetch((req) => {
            capturedBody = req.body;
            return { status: 200, body: { message: "OK", success: true } };
        });

        const db = new CortexDB("http://testserver");
        await db.setup.configure("OLLAMA", "llama3", "nomic-embed-text");

        expect(capturedBody.apiKey).toBeUndefined();
        expect(capturedBody.baseUrl).toBeUndefined();
    });

    it("configure() accepts lowercase provider string and normalises it", async () => {
        let capturedBody: any;

        mockFetch((req) => {
            capturedBody = req.body;
            return { status: 200, body: { message: "OK", success: true } };
        });

        const db = new CortexDB("http://testserver");
        const resp = await db.setup.configure("gemini", "gemini-2.0-flash", "gemini-embedding-001");

        expect(resp.success).toBe(true);
        // Provider must be uppercased in the request body
        expect(capturedBody.provider).toBe("GEMINI");
    });

    it("configure() accepts LLMApiProvider enum value", async () => {
        mockFetch(() => ({ status: 200, body: { message: "OK", success: true } }));

        const db = new CortexDB("http://testserver");
        const resp = await db.setup.configure(
            LLMApiProvider.ANTHROPIC,
            "claude-3-sonnet",
            "voyage-2",
        );

        expect(resp.success).toBe(true);
    });

    it("configure() throws on server error", async () => {
        mockFetch(() => ({ status: 500, body: { error: "Internal Server Error" } }));

        const db = new CortexDB("http://testserver");
        await expect(
            db.setup.configure("GEMINI", "gemini-2.0-flash", "gemini-embedding-001"),
        ).rejects.toThrow("CortexDB HTTP Error: 500");
    });
});

import { defineConfig } from "vitest/config";

/**
 * E2E test configuration.
 * - Runs tests in tests/e2e/**
 * - Automatically loads .env (GEMINI_API_KEY, GEMINI_CHAT_MODEL, GEMINI_EMBED_MODEL)
 * - Requires a real CortexDB server (set CORTEXDB_URL env var, defaults to http://localhost:8080)
 * - Tests automatically skip themselves if the server is unreachable
 */
export default defineConfig({
    test: {
        name: "e2e",
        include: ["tests/e2e/**/*.test.ts"],
        globals: true,
        environment: "node",
        testTimeout: 30_000,
        env: { NODE_ENV: "test" },
    },
});

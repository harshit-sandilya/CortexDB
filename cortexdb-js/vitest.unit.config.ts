import { defineConfig } from "vitest/config";

/**
 * Unit test configuration.
 * - Runs tests in tests/unit/**
 * - Uses global mocks for fetch — no real server needed
 */
export default defineConfig({
    test: {
        name: "unit",
        include: ["tests/unit/**/*.test.ts"],
        globals: true,
        environment: "node",
    },
});

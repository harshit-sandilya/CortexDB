// test.js
const { CortexDB, LLMApiProvider, ConverserRole } = require('./dist/index.js');

async function runTest() {
    console.log("=== Testing CortexDB JavaScript ORM ===");

    // 1. Initialize the client
    const db = new CortexDB("http://localhost:8080");
    console.log("Client created:", db.toString());

    try {
        // 2. Setup (Configure LLM)
        // Note: Make sure your CortexDB server is running on localhost:8080 before running this!
        console.log("\nAttempting to configure LLM...");
        const setupRes = await db.setup.configure(
            LLMApiProvider.GEMINI,
            "gemini-2.0-flash", // chat model
            "gemini-embedding-001", // embed model
            "my-fake-api-key"
        );
        console.log("Setup response:", setupRes);

        // 3. Ingest
        console.log("\nAttempting to ingest document...");
        const ingestRes = await db.ingest.document(
            "user-123",
            ConverserRole.USER,
            "Hello CortexDB, this is a test from the JS ORM!"
        );
        console.log("Ingest response:", ingestRes);

        // 4. Query
        console.log("\nAttempting to search contexts...");
        const queryRes = await db.query.searchContexts("Hello CortexDB");
        console.log("Query response:", queryRes);

    } catch (error) {
        console.error("\n[Error during test] Is your CortexDB Java server running?");
        console.error(error.message);
    }
}

runTest();

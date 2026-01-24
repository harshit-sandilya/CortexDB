package com.vectornode.memory.setup.service;

import com.vectornode.memory.entity.enums.LLMApiProvider;
import com.vectornode.memory.setup.dto.request.SetupRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class GeminiIntegrationTest {

    @Autowired
    private SetupService setupService;

    @Test
    @EnabledIfSystemProperty(named = "GEMINI_API_KEY", matches = ".*")
    void testGeminiIntegration() {
        String apiKey = System.getProperty("GEMINI_API_KEY");
        // Fallback to env if property is missing but annotation passed?
        if (apiKey == null) {
            apiKey = System.getenv("GEMINI_API_KEY");
        }

        SetupRequest request = new SetupRequest();
        request.setProvider(LLMApiProvider.GEMINI);
        request.setApiKey(apiKey);
        request.setModelName("gemini-2.0-flash");
        // baseUrl is optional

        setupService.configureLLM(request);

        // Verify LLM call works (probe)
        String result = LLMProvider.callLLM("Hello, are you online?");
        assertNotNull(result);
        System.out.println("Gemini Response: " + result);
    }
}

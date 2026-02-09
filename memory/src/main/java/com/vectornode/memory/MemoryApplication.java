package com.vectornode.memory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication(excludeName = {
		// Azure OpenAI autoconfigs
		"org.springframework.ai.autoconfigure.azure.openai.AzureOpenAiAutoConfiguration",
		"org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiAutoConfiguration",
		"org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiEmbeddingAutoConfiguration",
		"org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiChatAutoConfiguration",
		// OpenAI autoconfigs
		"org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration",
		"org.springframework.ai.model.openai.autoconfigure.OpenAiAutoConfiguration",
		"org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration",
		"org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration",
		"org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
		"org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration",
		"org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration",
		"org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration",
		// Ollama autoconfigs
		"org.springframework.ai.autoconfigure.ollama.OllamaAutoConfiguration",
		"org.springframework.ai.model.ollama.autoconfigure.OllamaAutoConfiguration",
		"org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration",
		"org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration",
		// PGVector autoconfig (both old and new package paths)
		"org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration",
		"org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration",
		// Broken autoconfigs from Spring Boot 3.5+ new package structure
		"org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration",
		"org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration",
		"org.springframework.boot.webmvc.autoconfigure.WebMvcObservationAutoConfiguration"
})
public class MemoryApplication {

	public static void main(String[] args) {
		SpringApplication.run(MemoryApplication.class, args);
	}

}

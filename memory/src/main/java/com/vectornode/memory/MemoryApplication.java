package com.vectornode.memory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication(excludeName = {
		"org.springframework.ai.autoconfigure.azure.openai.AzureOpenAiAutoConfiguration",
		"org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiAutoConfiguration",
		"org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration",
		"org.springframework.ai.model.openai.autoconfigure.OpenAiAutoConfiguration",
		"org.springframework.ai.autoconfigure.ollama.OllamaAutoConfiguration",
		"org.springframework.ai.model.ollama.autoconfigure.OllamaAutoConfiguration",
		"org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration"
})
public class MemoryApplication {

	public static void main(String[] args) {
		SpringApplication.run(MemoryApplication.class, args);
	}

}

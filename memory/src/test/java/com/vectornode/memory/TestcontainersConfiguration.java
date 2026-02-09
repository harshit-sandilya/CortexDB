package com.vectornode.memory;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// OllamaContainer is disabled for Docker-in-Docker compatibility
	// The tests use mocked LLM calls or Gemini API instead
	// @Bean
	// @ServiceConnection
	// OllamaContainer ollamaContainer() {
	// return new OllamaContainer(DockerImageName.parse("ollama/ollama:latest"));
	// }

	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> pgvectorContainer() {
		return new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"));
	}

}

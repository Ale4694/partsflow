package io.github.ale4694.partsflow;

import io.github.ale4694.partsflow.ai.search.FakeEmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** PostgreSQL for integration tests; same image as compose.yaml so pg_trgm is available. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/**
	 * A fake embedding model that wins over the real one (marked @Primary), so no integration test can ever call a real
	 * embedding service, whatever key is set. Tests that care about the vectors autowire it by type.
	 */
	@Bean
	@Primary
	FakeEmbeddingModel testEmbeddingModel() {
		return new FakeEmbeddingModel(768);
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer(
				DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
	}
}

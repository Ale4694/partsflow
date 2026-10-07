package io.github.ale4694.partsflow;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Only the PostgreSQL container, same image as compose.yaml (so pg_trgm and pgvector are available). Used on its own
 * by the opt-in evals that must talk to the REAL embedding model; every other test imports
 * {@link TestcontainersConfiguration}, which adds a fake embedding model on top.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PgvectorContainerConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer(
				DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
	}
}

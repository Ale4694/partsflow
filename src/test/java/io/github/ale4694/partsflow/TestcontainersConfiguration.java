package io.github.ale4694.partsflow;

import io.github.ale4694.partsflow.ai.search.FakeEmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * What every integration test imports: PostgreSQL for integration tests (see {@link PgvectorContainerConfiguration})
 * and a fake embedding model that wins over the real one (marked @Primary), so no integration test can ever call a
 * real embedding service, whatever key is set. Tests that care about the vectors autowire the fake by type.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(PgvectorContainerConfiguration.class)
public class TestcontainersConfiguration {

	@Bean
	@Primary
	FakeEmbeddingModel testEmbeddingModel() {
		return new FakeEmbeddingModel(768);
	}
}

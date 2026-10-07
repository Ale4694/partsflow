package io.github.ale4694.partsflow.ai.search;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Wiring of the semantic search: its settings and the background jobs (see {@link ItemEmbeddingIndexer}). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmbeddingProperties.class)
@EnableScheduling
class SearchConfig {
}

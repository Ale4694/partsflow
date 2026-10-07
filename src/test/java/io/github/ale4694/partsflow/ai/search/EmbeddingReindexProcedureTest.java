package io.github.ale4694.partsflow.ai.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ale4694.partsflow.TestcontainersConfiguration;
import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.catalog.ItemRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * The procedure of ADR 0012 for changing the vector SIZE after the database exists, run for real against
 * PostgreSQL: drop the table, forget that migration V6 ran, let Flyway apply it again with the new size.
 * (A change of the MODEL with the same size needs none of this: the indexer re-embeds by itself, see
 * ItemEmbeddingIndexerTest.aDifferentModelMakesEverythingStale.)
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = { "partsflow.ai.api-key=fake-key", "partsflow.embeddings.model=reindex-procedure-model" })
class EmbeddingReindexProcedureTest {

	@Autowired
	DataSource dataSource;
	@Autowired
	JdbcClient jdbc;
	@Autowired
	ItemEmbeddingRepository repository;
	@Autowired
	ItemRepository items;

	@Test
	void droppingTheTableAndRerunningTheMigrationGivesAColumnOfTheNewSize() {
		Item item = items.save(new Item("RIX-1", "Filtro olio", "PZ", BigDecimal.ZERO));
		repository.upsert(item.getId(), VectorMath.normalize(new FakeEmbeddingModel(768).embed("filtro")), "m", 768,
				"a".repeat(64), java.time.Instant.now());
		assertThat(repository.columnDimensions()).contains(768);

		// ADR 0012, "If the size changes": with the application stopped...
		jdbc.sql("DROP TABLE item_embedding").update();
		jdbc.sql("DELETE FROM flyway_schema_history WHERE version = '6'").update();
		// ...and at the next start Flyway applies V6 again, with the new LLM_EMBEDDING_DIMENSIONS
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
				.placeholders(Map.of("embeddingDimensions", "1024")).load().migrate();

		assertThat(repository.columnDimensions()).contains(1024);
		assertThat(repository.findItemSources(List.of(item.getId())).getFirst().storedHash()).isNull(); // to be re-embedded
	}
}

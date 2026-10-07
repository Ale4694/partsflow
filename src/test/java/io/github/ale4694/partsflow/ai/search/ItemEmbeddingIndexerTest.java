package io.github.ale4694.partsflow.ai.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.errors.ClientException;
import io.github.ale4694.partsflow.TestcontainersConfiguration;
import io.github.ale4694.partsflow.ai.search.ItemEmbeddingIndexer.IndexReport;
import io.github.ale4694.partsflow.ai.search.ItemEmbeddingIndexer.State;
import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.catalog.ItemRepository;
import io.github.ale4694.partsflow.catalog.Supplier;
import io.github.ale4694.partsflow.catalog.SupplierItemCode;
import io.github.ale4694.partsflow.catalog.SupplierItemCodeRepository;
import io.github.ale4694.partsflow.catalog.SupplierRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** The indexer against a real database and the fake embedding model: what is embedded, when, and how often. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "partsflow.ai.api-key=fake-key")
class ItemEmbeddingIndexerTest {

	private static final AtomicInteger COUNTER = new AtomicInteger();

	@Autowired
	ItemEmbeddingIndexer indexer;
	@Autowired
	ItemEmbeddingRepository repository;
	@Autowired
	EmbeddingGateway gateway;
	@Autowired
	EmbeddingSchemaGuard guard;
	@Autowired
	EmbeddingProperties properties;
	@Autowired
	FakeEmbeddingModel model;
	@Autowired
	ItemRepository items;
	@Autowired
	SupplierRepository suppliers;
	@Autowired
	SupplierItemCodeRepository codes;

	@BeforeEach
	void resetFake() {
		model.reset();
		indexer.resume();
	}

	private Item newItem(String description) {
		return items.save(new Item("IDX-" + COUNTER.incrementAndGet(), description, "PZ", BigDecimal.ZERO));
	}

	private List<Long> ids(Item... of) {
		return java.util.Arrays.stream(of).map(Item::getId).toList();
	}

	@Test
	void embedsNewItemsInOneRequestAndSkipsThemWhenNothingChanged() {
		Item a = newItem("Filtro olio motore");
		Item b = newItem("Pastiglie freni anteriori");

		IndexReport first = indexer.indexItems(ids(a, b));
		IndexReport second = indexer.indexItems(ids(a, b));

		assertThat(first).isEqualTo(new IndexReport(State.DONE, 2, 2, 0));
		assertThat(second).isEqualTo(new IndexReport(State.DONE, 0, 0, 0));
		assertThat(model.batchSizes()).containsExactly(2); // both items in ONE request, nothing for the second run
	}

	@Test
	void anItemWhoseDescriptionChangedIsEmbeddedAgainAndOthersAreNot() {
		Item changed = newItem("Candela accensione");
		Item untouched = newItem("Tergicristallo anteriore");
		indexer.indexItems(ids(changed, untouched));
		model.reset();

		changed.update(changed.getCode(), "Candela di accensione al platino", "PZ", BigDecimal.ZERO);
		items.save(changed);
		IndexReport report = indexer.indexItems(ids(changed, untouched));

		assertThat(report).isEqualTo(new IndexReport(State.DONE, 1, 1, 0));
		assertThat(model.batchSizes()).containsExactly(1);
	}

	@Test
	void aChangedSupplierCodeMakesTheItemStale() {
		Item item = newItem("Olio motore 5W30 5 litri");
		indexer.indexItems(ids(item));
		model.reset();
		Supplier supplier = suppliers.save(new Supplier("Ricambi Idx Srl", "8%010d".formatted(COUNTER.get())));

		codes.save(new SupplierItemCode(supplier, item, "RR-OIL-530"));
		IndexReport report = indexer.indexItems(ids(item));

		assertThat(report.embedded()).isEqualTo(1);
	}

	@Test
	void aDifferentModelMakesEverythingStale() {
		Item item = newItem("Cinghia distribuzione");
		indexer.indexItems(ids(item));
		ItemEmbeddingRepository.ItemSource source = repository.findItemSources(ids(item)).getFirst();

		assertThat(source.isStale(gateway.modelName(), gateway.dimensions(), source.sourceText().hash())).isFalse();
		assertThat(source.isStale("another-embedding-model", gateway.dimensions(), source.sourceText().hash())).isTrue();
	}

	@Test
	void largeCatalogsAreEmbeddedInBatchesOfTheConfiguredSize() {
		List<Item> many = java.util.stream.IntStream.range(0, properties.batchSize() + 5)
				.mapToObj(i -> newItem("Articolo di prova numero " + i)).toList();

		IndexReport report = indexer.indexItems(many.stream().map(Item::getId).toList());

		assertThat(report.embedded()).isEqualTo(many.size());
		assertThat(model.batchSizes()).containsExactly(properties.batchSize(), 5);
	}

	@Test
	void aQuotaErrorStopsTheRunLeavesItemsStaleAndPausesTheIndexer() {
		Item item = newItem("Batteria 60Ah");
		MutableClock clock = new MutableClock();
		ItemEmbeddingIndexer paused = new ItemEmbeddingIndexer(repository, gateway, guard, properties, clock);
		model.failWith(() -> new ClientException(429, "RESOURCE_EXHAUSTED", "Quota exceeded. Please retry in 2h."));

		IndexReport failed = paused.indexItems(ids(item));
		model.reset();
		IndexReport whilePaused = paused.indexItems(ids(item));
		clock.advance(Duration.ofHours(3));
		IndexReport afterThePause = paused.indexItems(ids(item));

		assertThat(failed.state()).isEqualTo(State.FAILED);
		assertThat(failed.failed()).isEqualTo(1);
		assertThat(whilePaused.state()).isEqualTo(State.PAUSED);
		assertThat(model.batchSizes()).containsExactly(1); // nothing was sent while paused
		assertThat(afterThePause).isEqualTo(new IndexReport(State.DONE, 1, 1, 0));
	}

	@Test
	void theVectorColumnMatchesTheConfiguredSize() {
		assertThat(guard.dimensionsMatch()).isTrue();
		assertThat(guard.columnDimensions()).isEqualTo(768);
	}

	/** A clock that only moves when the test says so. */
	static class MutableClock extends Clock {

		private Instant now = Instant.parse("2026-10-07T10:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}

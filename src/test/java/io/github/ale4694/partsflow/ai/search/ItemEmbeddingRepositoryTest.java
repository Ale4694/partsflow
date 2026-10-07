package io.github.ale4694.partsflow.ai.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ale4694.partsflow.TestcontainersConfiguration;
import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.catalog.ItemRepository;
import io.github.ale4694.partsflow.catalog.Supplier;
import io.github.ale4694.partsflow.catalog.SupplierItemCode;
import io.github.ale4694.partsflow.catalog.SupplierItemCodeRepository;
import io.github.ale4694.partsflow.catalog.SupplierRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** The migration and the hand-written SQL, against the real pgvector image. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ItemEmbeddingRepositoryTest {

	private static final AtomicInteger COUNTER = new AtomicInteger();
	private static final int DIMENSIONS = 768;

	@Autowired
	ItemEmbeddingRepository repository;
	@Autowired
	ItemRepository items;
	@Autowired
	SupplierRepository suppliers;
	@Autowired
	SupplierItemCodeRepository codes;

	private Item newItem(String description) {
		return items.save(new Item("EMB-" + COUNTER.incrementAndGet(), description, "PZ", BigDecimal.ZERO));
	}

	@Test
	void theMigrationCreatedAVectorColumnOfTheConfiguredSize() {
		assertThat(repository.columnDimensions()).contains(DIMENSIONS);
	}

	@Test
	void anItemWithoutEmbeddingIsStaleAndOneWithTheCurrentHashIsNot() {
		Item item = newItem("Filtro olio motore");
		ItemEmbeddingRepository.ItemSource before = repository.findItemSources(List.of(item.getId())).getFirst();
		assertThat(before.isStale("m", DIMENSIONS, before.sourceText().hash())).isTrue();

		FakeEmbeddingModel fake = new FakeEmbeddingModel(DIMENSIONS);
		repository.upsert(item.getId(), VectorMath.normalize(fake.embed(before.sourceText().text())), "m", DIMENSIONS,
				before.sourceText().hash(), Instant.now());

		ItemEmbeddingRepository.ItemSource after = repository.findItemSources(List.of(item.getId())).getFirst();
		assertThat(after.isStale("m", DIMENSIONS, after.sourceText().hash())).isFalse();
		assertThat(after.isStale("another-model", DIMENSIONS, after.sourceText().hash())).isTrue();
		assertThat(after.isStale("m", 1024, after.sourceText().hash())).isTrue();
	}

	@Test
	void theSourceTextIncludesTheSupplierCodesAndChangesWhenTheyDo() {
		Item item = newItem("Cartuccia lubrificante motore 1.2");
		Supplier supplier = suppliers.save(new Supplier("Ricambi Emb Srl", "7%010d".formatted(COUNTER.get())));
		codes.save(new SupplierItemCode(supplier, item, "RR-OIL-530"));

		ItemEmbeddingRepository.ItemSource source = repository.findItemSources(List.of(item.getId())).getFirst();

		assertThat(source.supplierCodes()).isEqualTo("RR-OIL-530");
		assertThat(source.sourceText().text()).contains("Cartuccia lubrificante motore 1.2").contains("RR-OIL-530");
		String hashWithCode = source.sourceText().hash();
		assertThat(ItemSourceText.of(source.code(), source.description(), "").hash()).isNotEqualTo(hashWithCode);
	}

	@Test
	void upsertReplacesTheRowAndCountsFollow() {
		Item item = newItem("Pastiglie freno anteriori");
		ItemEmbeddingRepository.Counts before = repository.counts("m2", DIMENSIONS);
		float[] vector = VectorMath.normalize(new FakeEmbeddingModel(DIMENSIONS).embed("pastiglie freno"));

		repository.upsert(item.getId(), vector, "m2", DIMENSIONS, "a".repeat(64), Instant.now());
		repository.upsert(item.getId(), vector, "m2", DIMENSIONS, "b".repeat(64), Instant.now());

		assertThat(repository.counts("m2", DIMENSIONS).indexed()).isEqualTo(before.indexed() + 1);
		assertThat(repository.findItemSources(List.of(item.getId())).getFirst().storedHash()).isEqualTo("b".repeat(64));
	}

	@Test
	void deletingAnItemDeletesItsEmbedding() {
		Item item = newItem("Candela accensione");
		repository.upsert(item.getId(), VectorMath.normalize(new FakeEmbeddingModel(DIMENSIONS).embed("candela")), "m3",
				DIMENSIONS, "c".repeat(64), Instant.now());
		assertThat(repository.counts("m3", DIMENSIONS).indexed()).isEqualTo(1);

		items.deleteById(item.getId());
		items.flush();

		assertThat(repository.counts("m3", DIMENSIONS).indexed()).isZero();
	}
}

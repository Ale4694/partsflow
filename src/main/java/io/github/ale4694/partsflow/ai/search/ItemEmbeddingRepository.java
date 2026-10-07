package io.github.ale4694.partsflow.ai.search;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain SQL for the item_embedding table. It is written by hand on purpose: the pgvector type and operators are
 * not part of JPA, and a reader can see exactly which query runs. A vector travels as text ("[0.1,0.2,...]") and is
 * converted with CAST(... AS vector); no extra library is needed.
 */
@Repository
class ItemEmbeddingRepository {

	/** An item with the text to embed and what is stored for it now (null when it was never embedded). */
	record ItemSource(long itemId, String code, String description, String supplierCodes, String storedHash,
			String storedModel, Integer storedDimensions) {

		ItemSourceText sourceText() {
			return ItemSourceText.of(code, description, supplierCodes);
		}

		/** Needs (re)embedding: never embedded, changed since, or embedded by another model or size. */
		boolean isStale(String model, int dimensions, String currentHash) {
			return storedHash == null || !storedHash.equals(currentHash) || !model.equals(storedModel)
					|| storedDimensions == null || storedDimensions != dimensions;
		}
	}

	record Counts(long items, long indexed) {
	}

	private final JdbcClient jdbc;

	ItemEmbeddingRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** The items (all of them when {@code itemIds} is null) with their embedding text and stored state. */
	List<ItemSource> findItemSources(Collection<Long> itemIds) {
		String sql = """
				SELECT i.id, i.code, i.description,
				       coalesce(string_agg(DISTINCT c.supplier_code, ', ' ORDER BY c.supplier_code), '') AS supplier_codes,
				       e.source_hash, e.model, e.dimensions
				FROM item i
				LEFT JOIN supplier_item_code c ON c.item_id = i.id
				LEFT JOIN item_embedding e ON e.item_id = i.id
				%s
				GROUP BY i.id, i.code, i.description, e.source_hash, e.model, e.dimensions
				ORDER BY i.id
				""".formatted(itemIds == null ? "" : "WHERE i.id IN (:ids)");
		JdbcClient.StatementSpec statement = jdbc.sql(sql);
		if (itemIds != null) {
			if (itemIds.isEmpty()) {
				return List.of();
			}
			statement = statement.param("ids", itemIds);
		}
		return statement.query((rs, row) -> new ItemSource(rs.getLong("id"), rs.getString("code"),
				rs.getString("description"), rs.getString("supplier_codes"), rs.getString("source_hash"),
				rs.getString("model"), (Integer) rs.getObject("dimensions"))).list();
	}

	/** Inserts or replaces the embedding of an item. */
	void upsert(long itemId, float[] vector, String model, int dimensions, String sourceHash, Instant now) {
		jdbc.sql("""
				INSERT INTO item_embedding (item_id, embedding, model, dimensions, source_hash, updated_at)
				VALUES (:itemId, CAST(:vector AS vector), :model, :dimensions, :hash, :now)
				ON CONFLICT (item_id) DO UPDATE
				SET embedding = EXCLUDED.embedding, model = EXCLUDED.model, dimensions = EXCLUDED.dimensions,
				    source_hash = EXCLUDED.source_hash, updated_at = EXCLUDED.updated_at
				""")
				.param("itemId", itemId).param("vector", VectorMath.toPgVector(vector)).param("model", model)
				.param("dimensions", dimensions).param("hash", sourceHash).param("now", Timestamp.from(now))
				.update();
	}

	Counts counts(String model, int dimensions) {
		return jdbc.sql("""
				SELECT (SELECT count(*) FROM item) AS items,
				       (SELECT count(*) FROM item_embedding WHERE model = :model AND dimensions = :dimensions) AS indexed
				""").param("model", model).param("dimensions", dimensions)
				.query((rs, row) -> new Counts(rs.getLong("items"), rs.getLong("indexed"))).single();
	}

	/**
	 * The size of the vector column as created by the migration. For pgvector the "type modifier" of a column
	 * is its number of dimensions.
	 */
	Optional<Integer> columnDimensions() {
		return jdbc.sql("""
				SELECT a.atttypmod FROM pg_attribute a
				WHERE a.attrelid = 'item_embedding'::regclass AND a.attname = 'embedding'
				""").query(Integer.class).optional();
	}
}

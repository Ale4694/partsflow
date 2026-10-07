package io.github.ale4694.partsflow.ai.search;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The two searches as plain SQL, so the whole ranking can be read in one place.
 * <p>
 * <b>Text ranking</b>: pg_trgm {@code similarity()} (0 to 1) of the query with the description and with the code,
 * whichever is higher. It finds typos and shared words, but not synonyms.
 * <p>
 * <b>Vector ranking</b>: COSINE distance ({@code <=>}) between the query's embedding and each item's embedding;
 * similarity = 1 - distance. Cosine, not inner product, because the 768-number Gemini vectors are not pre-normalised
 * (see VectorMath). The operator matches the index's {@code vector_cosine_ops}. It finds meaning, not spelling.
 * <p>
 * <b>Fusion (hybrid)</b>: Reciprocal Rank Fusion. Each ranking gives an item {@code 1 / (k + rank)} (rank 1 is the
 * best); the two numbers are added; an item missing from one ranking gets 0 from it. Only the ORDER of each ranking
 * counts, not the similarity values, which are on different scales (pg_trgm is mostly 0.1 to 0.6, cosine of related
 * texts 0.5 to 0.9). k = 60 is the constant of the original paper and makes the top ranks matter most without any
 * single ranking dominating.
 */
@Repository
class ItemSearchRepository {

	/** One result row. The two similarities are null when the item was not in that ranking. */
	record Row(long itemId, String code, String description, String unit, BigDecimal quantity, Double vectorSimilarity,
			Double textSimilarity, double fusedScore) {
	}

	private static final String TEXT_SIMILARITY = "greatest(similarity(i.description, :text), similarity(i.code, :text))";

	private final JdbcClient jdbc;

	ItemSearchRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Text ranking only: ordered by pg_trgm similarity; the fused score is that similarity. */
	List<Row> textOnly(String text, double minSimilarity, int limit) {
		return jdbc.sql("""
				SELECT i.id, i.code, i.description, i.unit, coalesce(st.quantity, 0) AS quantity,
				       CAST(NULL AS float8) AS vector_similarity,
				       %1$s AS text_similarity,
				       %1$s AS fused
				FROM item i
				LEFT JOIN stock st ON st.item_id = i.id
				WHERE %1$s >= :minSimilarity
				ORDER BY text_similarity DESC, i.code
				LIMIT :limit
				""".formatted(TEXT_SIMILARITY))
				.param("text", text).param("minSimilarity", minSimilarity).param("limit", limit)
				.query((rs, row) -> toRow(rs)).list();
	}

	/** Vector ranking and text ranking, fused with RRF. */
	List<Row> hybrid(String text, float[] queryVector, String model, int dimensions, int pool, int rrfK,
			double minTextSimilarity, double minVectorSimilarity, int limit) {
		return jdbc.sql("""
				WITH semantic AS (
				    SELECT e.item_id,
				           1 - (e.embedding <=> CAST(:vector AS vector)) AS similarity,
				           row_number() OVER (ORDER BY e.embedding <=> CAST(:vector AS vector)) AS rank
				    FROM item_embedding e
				    WHERE e.model = :model AND e.dimensions = :dimensions
				      AND 1 - (e.embedding <=> CAST(:vector AS vector)) >= :minVector
				    ORDER BY e.embedding <=> CAST(:vector AS vector)
				    LIMIT :pool
				), lexical AS (
				    SELECT i.id AS item_id,
				           %1$s AS similarity,
				           row_number() OVER (ORDER BY %1$s DESC, i.code) AS rank
				    FROM item i
				    WHERE %1$s >= :minText
				    ORDER BY similarity DESC, i.code
				    LIMIT :pool
				)
				SELECT i.id, i.code, i.description, i.unit, coalesce(st.quantity, 0) AS quantity,
				       s.similarity AS vector_similarity,
				       l.similarity AS text_similarity,
				       coalesce(1.0 / (:k + s.rank), 0) + coalesce(1.0 / (:k + l.rank), 0) AS fused
				FROM semantic s
				FULL OUTER JOIN lexical l ON l.item_id = s.item_id
				JOIN item i ON i.id = coalesce(s.item_id, l.item_id)
				LEFT JOIN stock st ON st.item_id = i.id
				ORDER BY fused DESC, i.code
				LIMIT :limit
				""".formatted(TEXT_SIMILARITY))
				.param("vector", VectorMath.toPgVector(queryVector)).param("text", text).param("model", model)
				.param("dimensions", dimensions).param("pool", pool).param("k", rrfK)
				.param("minText", minTextSimilarity).param("minVector", minVectorSimilarity).param("limit", limit)
				.query((rs, row) -> toRow(rs)).list();
	}

	private static Row toRow(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new Row(rs.getLong("id"), rs.getString("code"), rs.getString("description"), rs.getString("unit"),
				rs.getBigDecimal("quantity"), number(rs, "vector_similarity"), number(rs, "text_similarity"),
				rs.getDouble("fused"));
	}

	/** pg_trgm's similarity() is a float4 and pgvector's arithmetic a float8: read either as a Double (or null). */
	private static Double number(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
		Number value = (Number) rs.getObject(column);
		return value == null ? null : value.doubleValue();
	}
}

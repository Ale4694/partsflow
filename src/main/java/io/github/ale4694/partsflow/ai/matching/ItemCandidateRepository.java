package io.github.ale4694.partsflow.ai.matching;

import io.github.ale4694.partsflow.catalog.Item;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Fuzzy search on item descriptions with PostgreSQL's pg_trgm extension: texts are compared by the three-letter
 * sequences they share, so "Brake pads front axle" finds "Front brake pad set". No embeddings or extra service needed.
 * (Plain scan, no index: fine for a small catalog. A GIN index with gin_trgm_ops would speed up the % operator.)
 */
public interface ItemCandidateRepository extends Repository<Item, Long> {

	@Query(value = """
			select i.id as id, i.code as code, i.description as description, i.unit as unit,
			       similarity(i.description, :text) as score
			from item i
			where similarity(i.description, :text) >= :minSimilarity
			order by score desc, i.code
			limit :maxResults
			""", nativeQuery = true)
	List<ItemCandidate> findSimilar(@Param("text") String text, @Param("minSimilarity") double minSimilarity,
			@Param("maxResults") int maxResults);
}

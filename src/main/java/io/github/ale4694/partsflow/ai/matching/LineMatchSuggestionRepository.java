package io.github.ale4694.partsflow.ai.matching;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineMatchSuggestionRepository extends JpaRepository<LineMatchSuggestion, Long> {

	Optional<LineMatchSuggestion> findByDraftLineId(Long draftLineId);

	List<LineMatchSuggestion> findByDraftIdOrderByDraftLineId(Long draftId);
}

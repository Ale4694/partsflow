package io.github.ale4694.partsflow.invoiceimport.draft;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImportDraftRepository extends JpaRepository<ImportDraft, Long> {

	boolean existsBySupplierIdAndDocumentNumberAndDocumentDate(Long supplierId, String documentNumber,
			LocalDate documentDate);

	/**
	 * Loads a draft that the caller is about to change. OPTIMISTIC_FORCE_INCREMENT bumps the draft's version
	 * on commit, so two people changing the same draft at once (e.g. a double confirm) cannot both succeed.
	 */
	@Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
	@Query("select d from ImportDraft d where d.id = :id")
	Optional<ImportDraft> findForUpdate(@Param("id") Long id);

	@EntityGraph(attributePaths = "supplier")
	Page<ImportDraft> findAllBy(Pageable pageable);

	@EntityGraph(attributePaths = "supplier")
	Page<ImportDraft> findByStatus(DraftStatus status, Pageable pageable);
}

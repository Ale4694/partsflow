package io.github.ale4694.partsflow.invoiceimport;

import io.github.ale4694.partsflow.common.PageResponse;
import io.github.ale4694.partsflow.invoiceimport.domain.MalformedDocumentException;
import io.github.ale4694.partsflow.invoiceimport.draft.DraftStatus;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

	private final ImportService service;

	public ImportController(ImportService service) {
		this.service = service;
	}

	/** Upload a FatturaPA XML file: creates a draft. Stock is NOT changed until the draft is confirmed. */
	@PostMapping(path = "/fatturapa", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<DraftResponse> uploadFatturaPa(@RequestParam("file") MultipartFile file) throws IOException {
		if (file.isEmpty()) {
			throw new MalformedDocumentException("The uploaded file is empty");
		}
		DraftResponse draft;
		try (InputStream xml = file.getInputStream()) {
			draft = service.importFatturaPa(xml);
		}
		return ResponseEntity.created(URI.create("/api/imports/" + draft.id())).body(draft);
	}

	@GetMapping
	PageResponse<DraftSummaryResponse> list(@RequestParam(required = false) DraftStatus status,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return service.list(status, pageable);
	}

	@GetMapping("/{id}")
	DraftResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	/** Pick the item for a pending line (the supplier code is remembered for next time). */
	@PostMapping("/{id}/lines/{lineId}/resolve")
	DraftResponse resolveLine(@PathVariable Long id, @PathVariable Long lineId,
			@Valid @RequestBody ResolveLineRequest request) {
		return service.resolveLine(id, lineId, request.itemId());
	}

	@PostMapping("/{id}/lines/{lineId}/skip")
	DraftResponse skipLine(@PathVariable Long id, @PathVariable Long lineId) {
		return service.skipLine(id, lineId);
	}

	/** Writes the stock movements. Fails with 409 while lines are pending review or if already confirmed. */
	@PostMapping("/{id}/confirm")
	DraftResponse confirm(@PathVariable Long id) {
		return service.confirm(id);
	}

	/** Discards a draft that was not confirmed, e.g. to upload a corrected file. */
	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable Long id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}
}

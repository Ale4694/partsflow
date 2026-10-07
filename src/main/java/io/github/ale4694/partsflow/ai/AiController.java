package io.github.ale4694.partsflow.ai;

import io.github.ale4694.partsflow.ai.assistant.AssistantRequest;
import io.github.ale4694.partsflow.ai.assistant.AssistantResponse;
import io.github.ale4694.partsflow.ai.assistant.AssistantService;
import io.github.ale4694.partsflow.ai.matching.ItemMatchingService;
import io.github.ale4694.partsflow.ai.matching.MatchRunResponse;
import io.github.ale4694.partsflow.ai.matching.SuggestionResponse;
import io.github.ale4694.partsflow.ai.pdf.PdfImportService;
import io.github.ale4694.partsflow.invoiceimport.DraftResponse;
import io.github.ale4694.partsflow.invoiceimport.domain.MalformedDocumentException;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Every endpoint here answers 503 when the LLM is not available (no API key, quota exhausted).
 * None of them changes the stock: they create drafts and suggestions that a person still has to confirm.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

	private final LlmGateway gateway;
	private final PdfImportService pdfImportService;
	private final ItemMatchingService matchingService;
	private final AssistantService assistantService;

	public AiController(LlmGateway gateway, PdfImportService pdfImportService, ItemMatchingService matchingService,
			AssistantService assistantService) {
		this.gateway = gateway;
		this.pdfImportService = pdfImportService;
		this.matchingService = matchingService;
		this.assistantService = assistantService;
	}

	public record AiStatus(boolean available) {
	}

	/** Lets clients know up front whether the AI features can be used (never reveals the key). */
	@GetMapping("/status")
	AiStatus status() {
		return new AiStatus(gateway.isConfigured());
	}

	/** Upload a supplier PDF (invoice, credit note or delivery note): creates a draft to review, like an XML import. */
	@PostMapping(path = "/imports/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<DraftResponse> importPdf(@RequestParam("file") MultipartFile file) throws IOException {
		if (file.isEmpty()) {
			throw new MalformedDocumentException("The uploaded file is empty");
		}
		DraftResponse draft = pdfImportService.importPdf(file.getBytes());
		return ResponseEntity.created(URI.create("/api/imports/" + draft.id())).body(draft);
	}

	/** Asks the LLM to propose a catalog item for each pending line of a draft. */
	@PostMapping("/imports/{draftId}/suggest-matches")
	MatchRunResponse suggestMatches(@PathVariable Long draftId) {
		return matchingService.suggestMatches(draftId);
	}

	@GetMapping("/imports/{draftId}/suggestions")
	List<SuggestionResponse> suggestions(@PathVariable Long draftId) {
		return matchingService.list(draftId);
	}

	/** Accepting resolves the line with the suggested item and remembers the supplier code. */
	@PostMapping("/suggestions/{id}/accept")
	SuggestionResponse accept(@PathVariable Long id) {
		return matchingService.accept(id);
	}

	@PostMapping("/suggestions/{id}/reject")
	SuggestionResponse reject(@PathVariable Long id) {
		return matchingService.reject(id);
	}

	@PostMapping("/assistant")
	AssistantResponse ask(@Valid @RequestBody AssistantRequest request) {
		return assistantService.ask(request.question());
	}
}

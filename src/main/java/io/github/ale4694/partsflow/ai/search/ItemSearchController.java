package io.github.ale4694.partsflow.ai.search;

import io.github.ale4694.partsflow.common.BadRequestException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Item search by meaning and spelling. A separate resource from {@code GET /api/items?q=} on purpose: that one is a
 * plain "contains" filter returning a page of items; this one returns scored results and says which mode answered.
 */
@RestController
@RequestMapping("/api/items/search")
public class ItemSearchController {

	private static final int MAX_QUERY_LENGTH = 200;
	private static final int MAX_LIMIT = 50;

	private final ItemSearchService service;

	public ItemSearchController(ItemSearchService service) {
		this.service = service;
	}

	/**
	 * @param q the text to look for, e.g. "filtro olio Fiat Panda"
	 * @param mode "hybrid" (default: meaning and spelling, falling back to spelling alone) or "text" (spelling only)
	 * @param limit how many results, 1 to 50 (default 10)
	 */
	@GetMapping
	ItemSearchService.SearchResult search(@RequestParam String q, @RequestParam(defaultValue = "hybrid") String mode,
			@RequestParam(defaultValue = "10") int limit) {
		if (q.length() > MAX_QUERY_LENGTH) {
			throw new BadRequestException("La ricerca è troppo lunga (massimo " + MAX_QUERY_LENGTH + " caratteri)");
		}
		boolean semantic = switch (mode.toLowerCase(java.util.Locale.ROOT)) {
			case "hybrid" -> true;
			case "text" -> false;
			default -> throw new BadRequestException("Modalità di ricerca non valida: usa «hybrid» o «text»");
		};
		return service.search(q, Math.max(1, Math.min(limit, MAX_LIMIT)), semantic);
	}
}

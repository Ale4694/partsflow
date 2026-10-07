package io.github.ale4694.partsflow.common;

import java.util.Map;

/** The requested resource does not exist. Mapped to HTTP 404. The message is for the (Italian) user interface. */
public class ResourceNotFoundException extends RuntimeException {

	/** Italian name of each resource kind, with the matching ending of "non trovato/a". */
	private static final Map<String, String> ITALIAN_NAMES = Map.of(
			"Supplier", "Fornitore",
			"Item", "Articolo",
			"Supplier item code", "Codice articolo del fornitore",
			"Draft", "Bozza",
			"Draft line", "Riga della bozza",
			"Suggestion", "Suggerimento");

	private static final Map<String, Boolean> FEMININE = Map.of("Draft", true, "Draft line", true);

	/** @param resource the English name of the kind of resource (as used in the code), e.g. "Supplier" */
	public ResourceNotFoundException(String resource, Object id) {
		super(ITALIAN_NAMES.getOrDefault(resource, resource) + " " + id
				+ (FEMININE.getOrDefault(resource, false) ? " non trovata" : " non trovato"));
	}
}

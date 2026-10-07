package io.github.ale4694.partsflow.ai.search;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The text that is embedded for an item: its code, its description and the codes its suppliers use for it.
 * The supplier codes help because suppliers write codes like "RR-OIL-530" that carry meaning (oil, 5W30...).
 * The SHA-256 hash of this text tells whether the item changed since it was embedded.
 */
record ItemSourceText(String text) {

	static ItemSourceText of(String code, String description, String supplierCodes) {
		StringBuilder text = new StringBuilder("Codice: ").append(code).append(". Descrizione: ").append(description)
				.append('.');
		if (supplierCodes != null && !supplierCodes.isBlank()) {
			text.append(" Codici dei fornitori: ").append(supplierCodes).append('.');
		}
		return new ItemSourceText(text.toString());
	}

	String hash() {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("Every Java runtime has SHA-256", ex);
		}
	}
}

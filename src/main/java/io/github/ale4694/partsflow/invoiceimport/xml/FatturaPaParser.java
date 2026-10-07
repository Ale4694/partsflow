package io.github.ale4694.partsflow.invoiceimport.xml;

import io.github.ale4694.partsflow.invoiceimport.domain.Document;
import java.io.InputStream;
import org.springframework.stereotype.Component;

/**
 * Entry point for XML invoices: read the XML, map it to the domain model, validate it.
 * Plain deterministic code; no LLM is involved in this path.
 */
@Component
public class FatturaPaParser {

	private final FatturaPaReader reader = new FatturaPaReader();
	private final FatturaPaMapper mapper = new FatturaPaMapper();

	public Document parse(InputStream xml) {
		return mapper.toDocument(reader.read(xml));
	}
}

package io.github.ale4694.partsflow.ai.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

/** What the model is told about the answer format: the JSON schema generated from the record. */
class ExtractedDocumentSchemaTest {

	@Test
	void theSchemaShownToTheModelCarriesTheFieldDescriptions() {
		String format = new BeanOutputConverter<>(ExtractedDocument.class).getFormat();

		assertThat(format)
				.contains("documentType").contains("supplierVatNumber").contains("lines").contains("unitPrice")
				.contains("Name of the company that ISSUED the document")
				.contains("DELIVERY_NOTE")
				.contains("yyyy-MM-dd");
	}
}

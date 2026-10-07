package io.github.ale4694.partsflow.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ale4694.partsflow.ai.AiProperties;
import io.github.ale4694.partsflow.ai.pdf.ExtractedDocument;
import io.github.ale4694.partsflow.ai.pdf.ExtractedDocumentMapper;
import io.github.ale4694.partsflow.ai.pdf.PdfTextExtractor;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The accuracy eval only runs with a real API key, so this checks (offline) that its sample data is sound:
 * every PDF can be read, and a perfect extraction of each sample passes the same validation production uses.
 */
class EvalSamplesTest {

	private final PdfTextExtractor extractor = new PdfTextExtractor(
			new AiProperties(5, 5, 0.1, 10, 30000, new AiProperties.Retry(3, Duration.ofMillis(1), 2.0, Duration.ofSeconds(5)),
					AiProperties.Provider.GEMINI, "a-test-key", "", ""));
	private final ExtractedDocumentMapper mapper = new ExtractedDocumentMapper();

	@Test
	void everySamplePdfIsReadable() {
		for (EvalSamples.Sample sample : EvalSamples.all()) {
			String text = extractor.extract(sample.toPdf());

			assertThat(text).as(sample.name()).contains(sample.number()).contains(sample.supplier()).contains(sample.vat());
		}
	}

	@Test
	void pricedSamplesPrintTheTotalLikeAnItalianDocument() {
		String text = extractor.extract(EvalSamples.all().getFirst().toPdf());

		assertThat(text).contains("TOTALE DOCUMENTO EUR 597,80").contains("250,00");
	}

	@Test
	void aPerfectExtractionOfEverySamplePassesValidation() {
		for (EvalSamples.Sample sample : EvalSamples.all()) {
			List<ExtractedDocument.Line> lines = sample.lines().stream()
					.map(l -> new ExtractedDocument.Line(l.code(), l.description(), l.quantity(), l.unit(), l.unitPrice(),
							l.total(), sample.priced() ? "22" : null))
					.toList();
			ExtractedDocument perfect = new ExtractedDocument(sample.type(), sample.supplier(), sample.vat(),
					sample.number(), sample.isoDate(), "EUR",
					sample.priced() ? sample.expectedTotal().toPlainString() : null, lines);

			assertThat(mapper.toDocument(perfect).body().number()).as(sample.name()).isEqualTo(sample.number());
		}
	}

	@Test
	void expectedTotalsAreRight() {
		List<String> totals = EvalSamples.all().stream()
				.map(s -> s.expectedTotal() == null ? "none" : s.expectedTotal().toPlainString()).toList();

		assertThat(totals).containsExactly("597.80", "204.96", "61.00", "none", "292.80");
	}
}

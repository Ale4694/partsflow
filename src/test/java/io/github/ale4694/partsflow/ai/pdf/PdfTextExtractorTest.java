package io.github.ale4694.partsflow.ai.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.ale4694.partsflow.ai.AiProperties;
import io.github.ale4694.partsflow.invoiceimport.domain.InvalidDocumentException;
import io.github.ale4694.partsflow.invoiceimport.domain.MalformedDocumentException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PdfTextExtractorTest {

	private PdfTextExtractor extractor(int maxChars) {
		return new PdfTextExtractor(new AiProperties(5, 5, 0.1, 10, maxChars,
				new AiProperties.Retry(3, Duration.ofMillis(1), 2.0, Duration.ofSeconds(5))));
	}

	@Test
	void extractsTheTextOfAPdf() {
		byte[] pdf = SyntheticPdfs.withLines(List.of("FATTURA N. FT-9/2026", "RR-BRK-001 | Front brake pad set | 10"));

		String text = extractor(30000).extract(pdf);

		assertThat(text).contains("FATTURA N. FT-9/2026").contains("Front brake pad set");
	}

	@Test
	void aPdfWithoutTextIsRejected() {
		assertThatThrownBy(() -> extractor(30000).extract(SyntheticPdfs.blankPage()))
				.isInstanceOf(InvalidDocumentException.class)
				.hasMessageContaining("no extractable text");
	}

	@Test
	void textThatIsTooLongIsNotSentToTheLlm() {
		byte[] pdf = SyntheticPdfs.withLines(List.of("x".repeat(200)));

		assertThatThrownBy(() -> extractor(100).extract(pdf))
				.isInstanceOf(InvalidDocumentException.class)
				.hasMessageContaining("too much");
	}

	@Test
	void somethingThatIsNotAPdfIsMalformed() {
		assertThatThrownBy(() -> extractor(30000).extract("this is not a pdf".getBytes(StandardCharsets.UTF_8)))
				.isInstanceOf(MalformedDocumentException.class);
	}
}

package io.github.ale4694.partsflow.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.ale4694.partsflow.TestcontainersConfiguration;
import io.github.ale4694.partsflow.ai.AiUnavailableException;
import io.github.ale4694.partsflow.ai.LlmResponseException;
import io.github.ale4694.partsflow.ai.pdf.ExtractedDocument;
import io.github.ale4694.partsflow.ai.pdf.ExtractedDocumentMapper;
import io.github.ale4694.partsflow.ai.pdf.PdfImportService;
import io.github.ale4694.partsflow.invoiceimport.domain.InvalidDocumentException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Optional accuracy eval against the REAL Gemini API. Disabled unless LLM_API_KEY is set in the environment.
 * <p>
 * It sends a handful of synthetic PDFs (no real data) through the same extraction used in production and reports
 * how many fields were extracted correctly. The free tier allows only a few requests per minute, so the test
 * pauses between calls (default 15 s, change with EVAL_PAUSE_SECONDS). It does not fail on a low score: the point
 * is the report, printed and written to target/llm-eval-report.txt, so prompt changes can be compared.
 * <p>
 * Run it deliberately: with the key exported, a normal {@code ./mvnw verify} runs it too. To skip it,
 * run {@code env -u LLM_API_KEY ./mvnw verify}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@EnabledIfEnvironmentVariable(named = "LLM_API_KEY", matches = ".+")
class ExtractionEvalTest {

	@Autowired
	PdfImportService pdfImportService;
	@Autowired
	ExtractedDocumentMapper mapper;

	record Score(String sample, int correct, int total, String note) {
	}

	@Test
	void measureExtractionAccuracy() throws Exception {
		long pauseMillis = Long.parseLong(System.getenv().getOrDefault("EVAL_PAUSE_SECONDS", "15")) * 1000;
		List<Score> scores = new ArrayList<>();
		boolean first = true;
		for (EvalSamples.Sample sample : EvalSamples.all()) {
			if (!first) {
				Thread.sleep(pauseMillis); // respect the free-tier rate limit
			}
			first = false;
			try {
				scores.add(evaluate(sample, pdfImportService.extract(sample.toPdf())));
			}
			catch (AiUnavailableException | LlmResponseException ex) {
				scores.add(new Score(sample.name(), 0, 0, "not evaluated: " + ex.getMessage()));
			}
		}

		String report = report(scores);
		System.out.println(report);
		Files.writeString(Path.of("target", "llm-eval-report.txt"), report);

		int evaluated = scores.stream().mapToInt(Score::total).sum();
		assumeTrue(evaluated > 0, "The LLM was unavailable for every sample (quota or key problem): nothing to evaluate");
		assertThat(scores.stream().mapToInt(Score::correct).sum()).isPositive();
	}

	private Score evaluate(EvalSamples.Sample sample, ExtractedDocument got) {
		int correct = 0;
		int total = 0;
		total++;
		correct += check(sample.type().equals(upper(got.documentType())));
		total++;
		correct += check(sample.vat().equals(digits(got.supplierVatNumber())));
		total++;
		correct += check(sample.number().equals(trim(got.number())));
		total++;
		correct += check(sample.isoDate().equals(trim(got.date())));
		if (sample.priced()) {
			total++;
			correct += check(sameNumber(sample.expectedTotal().toPlainString(), got.totalAmount()));
		}
		total++;
		correct += check(got.lines() != null && got.lines().size() == sample.lines().size());
		for (int i = 0; i < sample.lines().size(); i++) {
			EvalSamples.Line expected = sample.lines().get(i);
			ExtractedDocument.Line line = got.lines() != null && got.lines().size() > i ? got.lines().get(i) : null;
			total += 2;
			correct += check(line != null && sameNumber(expected.quantity(), line.quantity()));
			correct += check(line != null && sameNumber(expected.total(), line.totalPrice()));
		}

		String validation;
		try {
			mapper.toDocument(got);
			validation = "passes validation";
		}
		catch (InvalidDocumentException ex) {
			validation = "rejected by validation: " + ex.getMessage();
		}
		return new Score(sample.name(), correct, total, validation);
	}

	private String report(List<Score> scores) {
		StringBuilder out = new StringBuilder("\n=== LLM extraction eval ===\n");
		int correct = 0;
		int total = 0;
		for (Score score : scores) {
			out.append(String.format(Locale.ROOT, "%-24s %2d/%-2d  %s%n", score.sample(), score.correct(), score.total(),
					score.note()));
			correct += score.correct();
			total += score.total();
		}
		out.append(String.format(Locale.ROOT, "%-24s %2d/%-2d  accuracy %.0f%%%n", "TOTAL", correct, total,
				total == 0 ? 0.0 : 100.0 * correct / total));
		return out.toString();
	}

	private static int check(boolean ok) {
		return ok ? 1 : 0;
	}

	private static boolean sameNumber(String expected, String actual) {
		if (expected == null || actual == null) {
			return expected == null && (actual == null || actual.isBlank() || actual.equalsIgnoreCase("null"));
		}
		try {
			return new BigDecimal(expected).compareTo(new BigDecimal(actual.trim())) == 0;
		}
		catch (NumberFormatException ex) {
			return false;
		}
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private static String upper(String value) {
		return trim(value).toUpperCase(Locale.ROOT);
	}

	private static String digits(String value) {
		return trim(value).replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT).replaceFirst("^IT(?=\\d{11}$)", "");
	}
}

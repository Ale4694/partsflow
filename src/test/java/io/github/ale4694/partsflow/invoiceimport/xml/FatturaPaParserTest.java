package io.github.ale4694.partsflow.invoiceimport.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.ale4694.partsflow.invoiceimport.domain.ArticleCode;
import io.github.ale4694.partsflow.invoiceimport.domain.CreditNote;
import io.github.ale4694.partsflow.invoiceimport.domain.Document;
import io.github.ale4694.partsflow.invoiceimport.domain.DocumentBody;
import io.github.ale4694.partsflow.invoiceimport.domain.DocumentLine;
import io.github.ale4694.partsflow.invoiceimport.domain.InvalidDocumentException;
import io.github.ale4694.partsflow.invoiceimport.domain.Invoice;
import io.github.ale4694.partsflow.invoiceimport.domain.MalformedDocumentException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FatturaPaParserTest {

	private final FatturaPaParser parser = new FatturaPaParser();

	private InputStream fixture(String name) {
		return getClass().getResourceAsStream("/fatturapa/" + name);
	}

	private InputStream xml(String content) {
		return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
	}

	private String fixtureText(String name) throws IOException {
		try (InputStream in = fixture(name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void parsesValidInvoiceWithPrefixedRoot() {
		Document document = parser.parse(fixture("invoice-valid.xml"));

		assertThat(document).isInstanceOf(Invoice.class);
		DocumentBody body = document.body();
		assertThat(body.tipoDocumento()).isEqualTo("TD01");
		assertThat(body.supplier().vatNumber()).isEqualTo("20000000001");
		assertThat(body.supplier().name()).isEqualTo("Ricambi Rossi Srl");
		assertThat(body.number()).isEqualTo("FT-0001/2026");
		assertThat(body.date()).isEqualTo(LocalDate.of(2026, 3, 10));
		assertThat(body.total()).isEqualByComparingTo("621.10");
		assertThat(body.lines()).hasSize(4);
		assertThat(body.summaries()).hasSize(2);
		assertThat(body.ddtReferences()).singleElement().satisfies(ddt -> {
			assertThat(ddt.number()).isEqualTo("DDT-0042");
			assertThat(ddt.date()).isEqualTo(LocalDate.of(2026, 3, 9));
		});
	}

	@Test
	void linesKeepExactDecimalsAndAllArticleCodes() {
		DocumentLine first = parser.parse(fixture("invoice-valid.xml")).body().lines().getFirst();

		assertThat(first.lineNumber()).isEqualTo(1);
		assertThat(first.description()).isEqualTo("Front brake pad set");
		assertThat(first.quantity()).isEqualByComparingTo("10");
		assertThat(first.unit()).isEqualTo("PZ");
		assertThat(first.unitPrice()).isEqualByComparingTo("25");
		assertThat(first.totalPrice()).isEqualByComparingTo("250");
		assertThat(first.vatRate()).isEqualByComparingTo("22");
		assertThat(first.articleCodes()).containsExactly(
				new ArticleCode("EAN", "8000000000011"), new ArticleCode("FORNITORE", "RR-BRK-001"));
	}

	@Test
	void serviceLinesHaveNoQuantityAndNoCodes() {
		DocumentLine transport = parser.parse(fixture("invoice-valid.xml")).body().lines().get(2);

		assertThat(transport.quantity()).isNull();
		assertThat(transport.articleCodes()).isEmpty();
	}

	@Test
	void parsesCreditNoteWithDefaultNamespace() {
		Document document = parser.parse(fixture("credit-note.xml"));

		assertThat(document).isInstanceOf(CreditNote.class);
		assertThat(document.body().number()).isEqualTo("NC-0007/2026");
		assertThat(document.body().total()).isEqualByComparingTo("61.00");
	}

	@Test
	void unknownCodesFixtureIsValidAsADocument() {
		Document document = parser.parse(fixture("unknown-codes.xml"));

		assertThat(document.body().lines()).hasSize(3);
		assertThat(document.body().lines().get(1).articleCodes()).isEmpty();
	}

	@Test
	void malformedXmlIsRejectedAsMalformed() {
		assertThatThrownBy(() -> parser.parse(fixture("malformed.xml")))
				.isInstanceOf(MalformedDocumentException.class)
				.hasMessageContaining("Not a readable FatturaPA XML file");
	}

	@Test
	void emptyFileIsMalformed() {
		assertThatThrownBy(() -> parser.parse(xml(""))).isInstanceOf(MalformedDocumentException.class);
	}

	@Test
	void validXmlThatIsNotAnInvoiceIsMalformed() {
		assertThatThrownBy(() -> parser.parse(xml("<html><body>hello</body></html>")))
				.isInstanceOf(MalformedDocumentException.class)
				.hasMessageContaining("Missing element");
	}

	@Test
	void totalsMismatchIsRejectedWithAClearMessage() {
		assertThatThrownBy(() -> parser.parse(fixture("totals-mismatch.xml")))
				.isInstanceOf(InvalidDocumentException.class)
				.hasMessageContaining("VAT rate 22.00")
				.hasMessageContaining("505.00")
				.hasMessageContaining("500.00");
	}

	@Test
	void unsupportedDocumentTypeIsRejected() throws IOException {
		String debitNote = fixtureText("invoice-valid.xml").replace("<TipoDocumento>TD01</TipoDocumento>",
				"<TipoDocumento>TD05</TipoDocumento>");

		assertThatThrownBy(() -> parser.parse(xml(debitNote)))
				.isInstanceOf(InvalidDocumentException.class)
				.hasMessageContaining("TD05");
	}

	@Test
	void deferredInvoiceTd24IsTreatedAsInvoice() throws IOException {
		String td24 = fixtureText("invoice-valid.xml").replace("<TipoDocumento>TD01</TipoDocumento>",
				"<TipoDocumento>TD24</TipoDocumento>");

		assertThat(parser.parse(xml(td24))).isInstanceOf(Invoice.class);
	}

	@Test
	void nonEuroCurrencyIsRejected() throws IOException {
		String usd = fixtureText("invoice-valid.xml").replace("<Divisa>EUR</Divisa>", "<Divisa>USD</Divisa>");

		assertThatThrownBy(() -> parser.parse(xml(usd))).isInstanceOf(InvalidDocumentException.class)
				.hasMessageContaining("EUR");
	}

	@Test
	void invalidNumberAndDateAreMalformed() throws IOException {
		String badNumber = fixtureText("invoice-valid.xml").replace("<Quantita>10.00000000</Quantita>",
				"<Quantita>ten</Quantita>");
		String badDate = fixtureText("invoice-valid.xml").replace("<Data>2026-03-10</Data>", "<Data>10/03/2026</Data>");

		assertThatThrownBy(() -> parser.parse(xml(badNumber))).isInstanceOf(MalformedDocumentException.class)
				.hasMessageContaining("Quantita");
		assertThatThrownBy(() -> parser.parse(xml(badDate))).isInstanceOf(MalformedDocumentException.class)
				.hasMessageContaining("Data");
	}

	@Test
	void quantityWithMoreThanThreeDecimalsIsRejectedInsteadOfRounded() throws IOException {
		String precise = fixtureText("invoice-valid.xml").replace("<Quantita>10.00000000</Quantita>",
				"<Quantita>10.0005</Quantita>");

		assertThatThrownBy(() -> parser.parse(xml(precise))).isInstanceOf(InvalidDocumentException.class)
				.hasMessageContaining("decimals");
	}

	@Test
	void externalEntitiesAreNotResolved() {
		String xxe = """
				<?xml version="1.0"?>
				<!DOCTYPE x [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
				<FatturaElettronica><FatturaElettronicaHeader><CedentePrestatore><DatiAnagrafici>
				<IdFiscaleIVA><IdCodice>&secret;</IdCodice></IdFiscaleIVA>
				</DatiAnagrafici></CedentePrestatore></FatturaElettronicaHeader></FatturaElettronica>""";

		assertThatThrownBy(() -> parser.parse(xml(xxe)))
				.isInstanceOf(MalformedDocumentException.class)
				.satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("root:"));
	}

	@Test
	void documentWithTwoBodiesIsNotSupported() throws IOException {
		String text = fixtureText("credit-note.xml");
		int start = text.indexOf("<FatturaElettronicaBody>");
		int end = text.indexOf("</FatturaElettronicaBody>") + "</FatturaElettronicaBody>".length();
		String body = text.substring(start, end);
		String twice = text.replace(body, body + body);

		assertThatThrownBy(() -> parser.parse(xml(twice))).isInstanceOf(InvalidDocumentException.class)
				.hasMessageContaining("several documents");
	}

	@Test
	void amountsAreExactBigDecimals() {
		BigDecimal sum = parser.parse(fixture("invoice-valid.xml")).body().lines().stream()
				.map(DocumentLine::totalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);

		assertThat(sum).isEqualByComparingTo("510.00");
	}
}

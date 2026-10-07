package io.github.ale4694.partsflow.ai.pdf;

import io.github.ale4694.partsflow.ai.AiProperties;
import io.github.ale4694.partsflow.invoiceimport.domain.InvalidDocumentException;
import io.github.ale4694.partsflow.invoiceimport.domain.MalformedDocumentException;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** Plain deterministic text extraction with PDFBox. Scanned documents (images only) are not supported. */
@Component
public class PdfTextExtractor {

	private static final int MAX_PAGES = 20;

	private final AiProperties properties;

	public PdfTextExtractor(AiProperties properties) {
		this.properties = properties;
	}

	public String extract(byte[] pdf) {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			if (document.getNumberOfPages() > MAX_PAGES) {
				throw new InvalidDocumentException("The PDF has more than " + MAX_PAGES + " pages");
			}
			String text = new PDFTextStripper().getText(document).strip();
			if (text.isEmpty()) {
				throw new InvalidDocumentException(
						"The PDF contains no extractable text (scanned documents are not supported)");
			}
			if (text.length() > properties.maxPdfTextChars()) {
				throw new InvalidDocumentException("The PDF text is longer than " + properties.maxPdfTextChars()
						+ " characters, which is too much to send to the LLM");
			}
			return text;
		}
		catch (IOException ex) {
			// PDFBox reports broken, truncated and password protected files as IOException
			throw new MalformedDocumentException("Not a readable PDF file", ex);
		}
	}
}

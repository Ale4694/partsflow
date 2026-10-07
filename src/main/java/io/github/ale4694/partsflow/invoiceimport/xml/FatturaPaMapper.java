package io.github.ale4694.partsflow.invoiceimport.xml;

import io.github.ale4694.partsflow.invoiceimport.domain.ArticleCode;
import io.github.ale4694.partsflow.invoiceimport.domain.CreditNote;
import io.github.ale4694.partsflow.invoiceimport.domain.DdtReference;
import io.github.ale4694.partsflow.invoiceimport.domain.Document;
import io.github.ale4694.partsflow.invoiceimport.domain.DocumentBody;
import io.github.ale4694.partsflow.invoiceimport.domain.DocumentLine;
import io.github.ale4694.partsflow.invoiceimport.domain.DocumentTotalsValidator;
import io.github.ale4694.partsflow.invoiceimport.domain.InvalidDocumentException;
import io.github.ale4694.partsflow.invoiceimport.domain.Invoice;
import io.github.ale4694.partsflow.invoiceimport.domain.MalformedDocumentException;
import io.github.ale4694.partsflow.invoiceimport.domain.Quantities;
import io.github.ale4694.partsflow.invoiceimport.domain.SupplierParty;
import io.github.ale4694.partsflow.invoiceimport.domain.VatSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Converts the XML mapping classes into our domain model, checking what the standard requires and what we support.
 * <ul>
 *   <li>Structural problems (missing element, bad number or date) throw {@link MalformedDocumentException}.</li>
 *   <li>Business rule problems (unsupported type, wrong currency, totals) throw {@link InvalidDocumentException}.</li>
 * </ul>
 */
public class FatturaPaMapper {

	public Document toDocument(FatturaPaXml xml) {
		FatturaPaXml.CedentePrestatore cedente = require(xml.header() == null ? null : xml.header().cedentePrestatore(),
				"FatturaElettronicaHeader/CedentePrestatore");
		SupplierParty supplier = toSupplier(cedente);

		if (xml.bodies() == null || xml.bodies().isEmpty()) {
			throw new MalformedDocumentException("Elemento mancante: FatturaElettronicaBody");
		}
		if (xml.bodies().size() > 1) {
			throw new InvalidDocumentException("I file che contengono più documenti non sono supportati");
		}
		FatturaPaXml.Body body = xml.bodies().getFirst();

		FatturaPaXml.DatiGenerali generali = require(body.datiGenerali(), "DatiGenerali");
		FatturaPaXml.DatiGeneraliDocumento documento = require(generali.datiGeneraliDocumento(),
				"DatiGeneraliDocumento");
		String tipoDocumento = requireText(documento.tipoDocumento(), "TipoDocumento");
		String divisa = requireText(documento.divisa(), "Divisa");
		if (!"EUR".equals(divisa)) {
			throw new InvalidDocumentException("Sono supportati solo documenti in EUR, trovato " + divisa);
		}

		FatturaPaXml.DatiBeniServizi beni = require(body.datiBeniServizi(), "DatiBeniServizi");
		List<DocumentLine> lines = list(beni.dettaglioLinee()).stream().map(this::toLine).toList();
		List<VatSummary> summaries = list(beni.datiRiepilogo()).stream().map(this::toSummary).toList();
		if (lines.isEmpty()) {
			throw new MalformedDocumentException("Elemento mancante: DettaglioLinee");
		}
		if (summaries.isEmpty()) {
			throw new MalformedDocumentException("Elemento mancante: DatiRiepilogo");
		}

		BigDecimal total = documento.importoTotaleDocumento() != null && !documento.importoTotaleDocumento().isBlank()
				? decimal(documento.importoTotaleDocumento(), "ImportoTotaleDocumento")
				: summaries.stream().map(s -> s.taxableAmount().add(s.taxAmount())).reduce(BigDecimal.ZERO,
						BigDecimal::add);

		DocumentBody documentBody = new DocumentBody(tipoDocumento, supplier,
				requireText(documento.numero(), "Numero"), date(documento.data(), "Data"), total, lines, summaries,
				list(generali.datiDdt()).stream().map(this::toDdt).toList());

		Document document = switch (tipoDocumento) {
			case "TD01", "TD24" -> new Invoice(documentBody);
			case "TD04" -> new CreditNote(documentBody);
			default -> throw new InvalidDocumentException(
					"Il tipo di documento " + tipoDocumento + " non è supportato (supportati: TD01, TD04, TD24)");
		};
		DocumentTotalsValidator.validate(documentBody);
		return document;
	}

	private SupplierParty toSupplier(FatturaPaXml.CedentePrestatore cedente) {
		FatturaPaXml.DatiAnagraficiCedente dati = require(cedente.datiAnagrafici(), "CedentePrestatore/DatiAnagrafici");
		FatturaPaXml.IdFiscaleIva id = require(dati.idFiscaleIva(), "IdFiscaleIVA");
		FatturaPaXml.Anagrafica anagrafica = require(dati.anagrafica(), "Anagrafica");
		String name = anagrafica.denominazione() != null && !anagrafica.denominazione().isBlank()
				? anagrafica.denominazione().trim()
				: (text(anagrafica.nome()) + " " + text(anagrafica.cognome())).trim();
		if (name.isEmpty()) {
			throw new MalformedDocumentException("Nome del fornitore mancante (Anagrafica)");
		}
		return new SupplierParty(requireText(id.idCodice(), "IdCodice").toUpperCase(Locale.ROOT), name);
	}

	private DocumentLine toLine(FatturaPaXml.DettaglioLinee line) {
		BigDecimal quantity = null;
		if (line.quantita() != null && !line.quantita().isBlank()) {
			quantity = Quantities.normalize(decimal(line.quantita(), "Quantita"));
		}
		BigDecimal unitPrice = line.prezzoUnitario() == null || line.prezzoUnitario().isBlank() ? null
				: decimal(line.prezzoUnitario(), "PrezzoUnitario");
		List<ArticleCode> codes = list(line.codiceArticolo()).stream()
				.filter(c -> c.codiceValore() != null && !c.codiceValore().isBlank())
				.map(c -> new ArticleCode(text(c.codiceTipo()), c.codiceValore().trim()))
				.toList();
		return new DocumentLine(
				(int) whole(requireText(line.numeroLinea(), "NumeroLinea"), "NumeroLinea"),
				codes,
				requireText(line.descrizione(), "Descrizione"),
				quantity,
				blankToNull(line.unitaMisura()),
				unitPrice,
				decimal(requireText(line.prezzoTotale(), "PrezzoTotale"), "PrezzoTotale"),
				decimal(requireText(line.aliquotaIva(), "AliquotaIVA"), "AliquotaIVA"));
	}

	private VatSummary toSummary(FatturaPaXml.DatiRiepilogo summary) {
		return new VatSummary(
				decimal(requireText(summary.aliquotaIva(), "AliquotaIVA"), "AliquotaIVA"),
				decimal(requireText(summary.imponibileImporto(), "ImponibileImporto"), "ImponibileImporto"),
				decimal(requireText(summary.imposta(), "Imposta"), "Imposta"));
	}

	private DdtReference toDdt(FatturaPaXml.DatiDdt ddt) {
		LocalDate date = ddt.dataDdt() == null || ddt.dataDdt().isBlank() ? null : date(ddt.dataDdt(), "DataDDT");
		return new DdtReference(requireText(ddt.numeroDdt(), "NumeroDDT"), date);
	}

	// ---- small parsing helpers: always from text, never from double ----

	private BigDecimal decimal(String text, String element) {
		try {
			return new BigDecimal(text.trim());
		}
		catch (NumberFormatException ex) {
			throw new MalformedDocumentException("Numero non valido in " + element + ": '" + text + "'");
		}
	}

	private long whole(String text, String element) {
		try {
			return Long.parseLong(text.trim());
		}
		catch (NumberFormatException ex) {
			throw new MalformedDocumentException("Numero non valido in " + element + ": '" + text + "'");
		}
	}

	private LocalDate date(String text, String element) {
		try {
			return LocalDate.parse(requireText(text, element));
		}
		catch (DateTimeParseException ex) {
			throw new MalformedDocumentException("Data non valida in " + element + ": '" + text + "' (atteso aaaa-mm-gg)");
		}
	}

	private <T> T require(T value, String element) {
		if (value == null) {
			throw new MalformedDocumentException("Elemento mancante: " + element);
		}
		return value;
	}

	private String requireText(String value, String element) {
		if (value == null || value.isBlank()) {
			throw new MalformedDocumentException("Elemento mancante: " + element);
		}
		return value.trim();
	}

	private String text(String value) {
		return value == null ? "" : value.trim();
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private <T> List<T> list(List<T> value) {
		return value == null ? List.of() : value;
	}
}

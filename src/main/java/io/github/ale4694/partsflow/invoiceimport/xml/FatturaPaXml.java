package io.github.ale4694.partsflow.invoiceimport.xml;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The shape of a FatturaPA XML file (schema v1.2.x), and nothing else: only the elements we use.
 * Everything is a String on purpose. Converting to numbers and dates happens in {@link FatturaPaMapper},
 * where a bad value can be reported with a clear message and money never touches double.
 * Element names follow the official standard; unknown elements are ignored by the reader.
 */
public record FatturaPaXml(
		@JsonProperty("FatturaElettronicaHeader") Header header,
		@JsonProperty("FatturaElettronicaBody") List<Body> bodies) {

	public record Header(@JsonProperty("CedentePrestatore") CedentePrestatore cedentePrestatore) {
	}

	/** The supplier (the party issuing the invoice). */
	public record CedentePrestatore(@JsonProperty("DatiAnagrafici") DatiAnagraficiCedente datiAnagrafici) {
	}

	public record DatiAnagraficiCedente(
			@JsonProperty("IdFiscaleIVA") IdFiscaleIva idFiscaleIva,
			@JsonProperty("Anagrafica") Anagrafica anagrafica) {
	}

	public record IdFiscaleIva(
			@JsonProperty("IdPaese") String idPaese,
			@JsonProperty("IdCodice") String idCodice) {
	}

	public record Anagrafica(
			@JsonProperty("Denominazione") String denominazione,
			@JsonProperty("Nome") String nome,
			@JsonProperty("Cognome") String cognome) {
	}

	public record Body(
			@JsonProperty("DatiGenerali") DatiGenerali datiGenerali,
			@JsonProperty("DatiBeniServizi") DatiBeniServizi datiBeniServizi) {
	}

	public record DatiGenerali(
			@JsonProperty("DatiGeneraliDocumento") DatiGeneraliDocumento datiGeneraliDocumento,
			@JsonProperty("DatiDDT") List<DatiDdt> datiDdt) {
	}

	public record DatiGeneraliDocumento(
			@JsonProperty("TipoDocumento") String tipoDocumento,
			@JsonProperty("Divisa") String divisa,
			@JsonProperty("Data") String data,
			@JsonProperty("Numero") String numero,
			@JsonProperty("ImportoTotaleDocumento") String importoTotaleDocumento) {
	}

	public record DatiDdt(
			@JsonProperty("NumeroDDT") String numeroDdt,
			@JsonProperty("DataDDT") String dataDdt) {
	}

	public record DatiBeniServizi(
			@JsonProperty("DettaglioLinee") List<DettaglioLinee> dettaglioLinee,
			@JsonProperty("DatiRiepilogo") List<DatiRiepilogo> datiRiepilogo) {
	}

	public record DettaglioLinee(
			@JsonProperty("NumeroLinea") String numeroLinea,
			@JsonProperty("CodiceArticolo") List<CodiceArticolo> codiceArticolo,
			@JsonProperty("Descrizione") String descrizione,
			@JsonProperty("Quantita") String quantita,
			@JsonProperty("UnitaMisura") String unitaMisura,
			@JsonProperty("PrezzoUnitario") String prezzoUnitario,
			@JsonProperty("PrezzoTotale") String prezzoTotale,
			@JsonProperty("AliquotaIVA") String aliquotaIva) {
	}

	public record CodiceArticolo(
			@JsonProperty("CodiceTipo") String codiceTipo,
			@JsonProperty("CodiceValore") String codiceValore) {
	}

	public record DatiRiepilogo(
			@JsonProperty("AliquotaIVA") String aliquotaIva,
			@JsonProperty("ImponibileImporto") String imponibileImporto,
			@JsonProperty("Imposta") String imposta) {
	}
}

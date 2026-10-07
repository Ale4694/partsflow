package io.github.ale4694.partsflow.ai.eval;

import io.github.ale4694.partsflow.ai.pdf.SyntheticPdfs;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Synthetic supplier documents with known correct answers. Everything here is invented. */
final class EvalSamples {

	record Line(String code, String description, String unit, String quantity, String unitPrice, String total) {
	}

	record Sample(String name, String type, String title, String vat, String supplier, String number, String isoDate,
			String printedDate, List<Line> lines, boolean priced) {

		/** Lines total plus 22% VAT, null for unpriced documents. */
		BigDecimal expectedTotal() {
			if (!priced) {
				return null;
			}
			BigDecimal taxable = lines.stream().map(l -> new BigDecimal(l.total())).reduce(BigDecimal.ZERO, BigDecimal::add);
			return taxable.add(taxable.multiply(new BigDecimal("0.22")).setScale(2, RoundingMode.HALF_UP));
		}

		byte[] toPdf() {
			List<String> text = new ArrayList<>();
			text.add(supplier);
			text.add("Via Inventata 1, 00100 Cittafittizia - P.IVA " + vat);
			text.add("");
			text.add(title + " n. " + number + "   del " + printedDate);
			text.add("Spett.le Partsflow Distribuzione Srl - P.IVA 20000000099");
			text.add("");
			text.add(priced ? "Codice | Descrizione | UM | Q.ta | Prezzo | Importo | IVA %"
					: "Codice | Descrizione | UM | Q.ta");
			for (Line line : lines) {
				text.add(priced
						? "%s | %s | %s | %s | %s | %s | 22".formatted(nullToEmpty(line.code()), line.description(),
								nullToEmpty(line.unit()), nullToEmpty(line.quantity()), italian(line.unitPrice()),
								italian(line.total()))
						: "%s | %s | %s | %s".formatted(nullToEmpty(line.code()), line.description(), line.unit(),
								line.quantity()));
			}
			if (priced) {
				BigDecimal total = expectedTotal();
				BigDecimal taxable = lines.stream().map(l -> new BigDecimal(l.total())).reduce(BigDecimal.ZERO,
						BigDecimal::add);
				text.add("");
				text.add("Totale imponibile EUR " + italian(taxable.toPlainString()));
				text.add("IVA 22% EUR " + italian(total.subtract(taxable).toPlainString()));
				text.add("TOTALE DOCUMENTO EUR " + italian(total.toPlainString()));
			}
			return SyntheticPdfs.withLines(text);
		}

		private static String nullToEmpty(String value) {
			return value == null ? "" : value;
		}

		/** 1234.50 becomes 1.234,50 like on an Italian document. */
		private static String italian(String plain) {
			if (plain == null) {
				return "";
			}
			BigDecimal value = new BigDecimal(plain).setScale(2, RoundingMode.HALF_UP);
			String[] parts = value.toPlainString().split("\\.");
			String integer = parts[0].replaceAll("(\\d)(?=(\\d{3})+$)", "$1.");
			return integer + "," + parts[1];
		}
	}

	static List<Sample> all() {
		return List.of(
				new Sample("italian-invoice", "INVOICE", "FATTURA", "20000000001", "Ricambi Rossi Srl", "FT-0101/2026",
						"2026-03-12", "12/03/2026", List.of(
								new Line("RR-BRK-001", "Pastiglie freno anteriori", "PZ", "10", "25.00", "250.00"),
								new Line("RR-OIL-530", "Olio motore 5W-30 lattina 5 litri", "PZ", "6", "40.00", "240.00")),
						true),
				new Sample("invoice-three-lines", "INVOICE", "FATTURA", "20000000002", "Autoricambi Bianchi Spa",
						"2026/778", "2026-04-02", "02/04/2026", List.of(
								new Line("AB-SPK-04", "Spark plug set", "SET", "4", "18.50", "74.00"),
								new Line("AB-AIR-12", "Air filter", "PZ", "3", "12.00", "36.00"),
								new Line("AB-WIP-60", "Wiper blade 600mm", "PZ", "8", "7.25", "58.00")),
						true),
				new Sample("credit-note", "CREDIT_NOTE", "NOTA DI CREDITO", "20000000001", "Ricambi Rossi Srl",
						"NC-0033/2026", "2026-04-15", "15/04/2026",
						List.of(new Line("RR-BRK-001", "Pastiglie freno anteriori (reso)", "PZ", "2", "25.00", "50.00")),
						true),
				new Sample("delivery-note", "DELIVERY_NOTE", "DOCUMENTO DI TRASPORTO", "20000000002",
						"Autoricambi Bianchi Spa", "DDT-0555", "2026-04-20", "20/04/2026", List.of(
								new Line("AB-AIR-12", "Air filter", "PZ", "12", null, null),
								new Line("AB-SPK-04", "Spark plug set", "SET", "6", null, null)),
						false),
				new Sample("invoice-with-transport", "INVOICE", "FATTURA", "20000000001", "Ricambi Rossi Srl",
						"FT-0150/2026", "2026-05-06", "06/05/2026", List.of(
								new Line("RR-DSC-210", "Dischi freno ventilati", "PZ", "5", "45.00", "225.00"),
								new Line(null, "Trasporto", null, null, null, "15.00")),
						true));
	}
}

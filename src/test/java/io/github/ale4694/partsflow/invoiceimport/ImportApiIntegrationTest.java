package io.github.ale4694.partsflow.invoiceimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ale4694.partsflow.TestcontainersConfiguration;
import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.catalog.ItemRepository;
import io.github.ale4694.partsflow.catalog.Supplier;
import io.github.ale4694.partsflow.catalog.SupplierItemCode;
import io.github.ale4694.partsflow.catalog.SupplierItemCodeRepository;
import io.github.ale4694.partsflow.catalog.SupplierRepository;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * The whole import flow against a real database. Each test gets its own supplier, items and document numbers,
 * because all tests share one database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ImportApiIntegrationTest {

	private static final AtomicInteger COUNTER = new AtomicInteger();

	@Autowired
	MockMvc mvc;
	@Autowired
	SupplierRepository suppliers;
	@Autowired
	ItemRepository items;
	@Autowired
	SupplierItemCodeRepository mappings;

	private String vat;
	private Supplier supplier;
	private Item brakePads;
	private Item engineOil;

	@BeforeEach
	void setUp() {
		int n = COUNTER.incrementAndGet();
		vat = "3%010d".formatted(n);
		supplier = suppliers.save(new Supplier("Ricambi Rossi Srl", vat));
		brakePads = items.save(new Item("IMP-BRK-" + n, "Front brake pad set", "PZ", BigDecimal.ZERO));
		engineOil = items.save(new Item("IMP-OIL-" + n, "Engine oil 5W-30", "PZ", BigDecimal.ZERO));
		mappings.save(new SupplierItemCode(supplier, brakePads, "RR-BRK-001"));
		mappings.save(new SupplierItemCode(supplier, engineOil, "RR-OIL-530"));
	}

	// ---- helpers ----

	/** A fixture with this test's supplier VAT and a unique document number. */
	private byte[] fixture(String name) throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/fatturapa/" + name)) {
			String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8)
					.replace("20000000001", vat)
					.replaceAll("<Numero>[^<]*</Numero>", "<Numero>" + vat + "-" + name + "</Numero>");
			return xml.getBytes(StandardCharsets.UTF_8);
		}
	}

	private ResultActions upload(byte[] xml) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/imports/fatturapa")
				.file(new MockMultipartFile("file", "invoice.xml", "application/xml", xml));
		return mvc.perform(request);
	}

	private long uploadOk(String fixtureName) throws Exception {
		String location = upload(fixture(fixtureName)).andExpect(status().isCreated()).andReturn().getResponse()
				.getHeader("Location");
		return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}

	private double stockOf(Item item) throws Exception {
		String body = mvc.perform(get("/api/inventory/stock/" + item.getId())).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString();
		return Double.parseDouble(body.replaceAll(".*\"quantity\":([0-9.]+).*", "$1"));
	}

	private void addStock(Item item, String quantity) throws Exception {
		mvc.perform(post("/api/inventory/movements").contentType(MediaType.APPLICATION_JSON).content("""
				{"itemId": %d, "type": "IN", "quantity": %s, "reason": "opening stock"}""".formatted(item.getId(),
				quantity))).andExpect(status().isCreated());
	}

	private long lineId(long draftId, int index) throws Exception {
		String body = mvc.perform(get("/api/imports/" + draftId)).andReturn().getResponse().getContentAsString();
		Matcher m = Pattern.compile("\"lines\":\\[(.*)]").matcher(body);
		assertThat(m.find()).isTrue();
		Matcher ids = Pattern.compile("\\{\"id\":(\\d+),\"lineNumber\"").matcher(m.group(1));
		long id = -1;
		for (int i = 0; i <= index; i++) {
			assertThat(ids.find()).isTrue();
			id = Long.parseLong(ids.group(1));
		}
		return id;
	}

	// ---- tests ----

	@Test
	void uploadCreatesADraftAndDoesNotTouchTheStock() throws Exception {
		long draftId = uploadOk("invoice-valid.xml");

		mvc.perform(get("/api/imports/" + draftId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.supplierName").value("Ricambi Rossi Srl"))
				.andExpect(jsonPath("$.tipoDocumento").value("TD01"))
				.andExpect(jsonPath("$.total").value(621.10))
				.andExpect(jsonPath("$.pendingLines").value(0))
				.andExpect(jsonPath("$.ddtReferences[0].number").value("DDT-0042"))
				.andExpect(jsonPath("$.lines.length()").value(4))
				// the second printed code (RR-BRK-001) is the one we know
				.andExpect(jsonPath("$.lines[0].status").value("MATCHED"))
				.andExpect(jsonPath("$.lines[0].supplierCode").value("RR-BRK-001"))
				.andExpect(jsonPath("$.lines[0].itemCode").value(brakePads.getCode()))
				.andExpect(jsonPath("$.lines[0].stockDelta").value(10))
				.andExpect(jsonPath("$.lines[1].status").value("MATCHED"))
				// transport and fees have no quantity: never stock
				.andExpect(jsonPath("$.lines[2].status").value("SKIPPED"))
				.andExpect(jsonPath("$.lines[3].status").value("SKIPPED"));

		assertThat(stockOf(brakePads)).isZero();
		assertThat(stockOf(engineOil)).isZero();
	}

	@Test
	void confirmWritesTheStockMovementsOnce() throws Exception {
		long draftId = uploadOk("invoice-valid.xml");

		mvc.perform(post("/api/imports/" + draftId + "/confirm"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.confirmedAt").isNotEmpty());

		assertThat(stockOf(brakePads)).isEqualTo(10.0);
		assertThat(stockOf(engineOil)).isEqualTo(6.0);
		mvc.perform(get("/api/inventory/movements?itemId=" + brakePads.getId()))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].type").value("IN"))
				.andExpect(jsonPath("$.content[0].sourceDocument").value(
						Matchers.startsWith("TD01 " + vat + "-invoice-valid.xml of 2026-03-10")));

		// confirming again must not double the stock
		mvc.perform(post("/api/imports/" + draftId + "/confirm")).andExpect(status().isConflict());
		assertThat(stockOf(brakePads)).isEqualTo(10.0);
		// and a confirmed draft cannot be deleted
		mvc.perform(delete("/api/imports/" + draftId)).andExpect(status().isConflict());
	}

	@Test
	void importingTheSameDocumentTwiceIsRejectedUntilTheDraftIsDeleted() throws Exception {
		long draftId = uploadOk("invoice-valid.xml");

		upload(fixture("invoice-valid.xml")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("già stato importato")));

		mvc.perform(delete("/api/imports/" + draftId)).andExpect(status().isNoContent());
		uploadOk("invoice-valid.xml");
	}

	@Test
	void unknownCodesBecomePendingAndMustBeReviewedBeforeConfirming() throws Exception {
		long draftId = uploadOk("unknown-codes.xml");

		mvc.perform(get("/api/imports/" + draftId))
				.andExpect(jsonPath("$.pendingLines").value(2))
				.andExpect(jsonPath("$.lines[0].status").value("PENDING_REVIEW"))
				.andExpect(jsonPath("$.lines[0].supplierCode").value("UNK-777"))
				.andExpect(jsonPath("$.lines[1].status").value("PENDING_REVIEW"))
				.andExpect(jsonPath("$.lines[1].supplierCode").doesNotExist())
				.andExpect(jsonPath("$.lines[2].status").value("MATCHED"));

		// cannot confirm while lines are pending, and nothing was loaded
		mvc.perform(post("/api/imports/" + draftId + "/confirm")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("righe da verificare")));
		assertThat(stockOf(engineOil)).isZero();

		Item timingBelt = items.save(new Item("IMP-TB-" + vat, "Timing belt kit", "PZ", BigDecimal.ZERO));
		mvc.perform(post("/api/imports/" + draftId + "/lines/" + lineId(draftId, 0) + "/resolve")
				.contentType(MediaType.APPLICATION_JSON).content("{\"itemId\": " + timingBelt.getId() + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pendingLines").value(1))
				.andExpect(jsonPath("$.lines[0].status").value("MATCHED"))
				.andExpect(jsonPath("$.lines[0].itemCode").value(timingBelt.getCode()));
		mvc.perform(post("/api/imports/" + draftId + "/lines/" + lineId(draftId, 1) + "/skip"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pendingLines").value(0));

		// a resolved or skipped line can not be resolved again
		mvc.perform(post("/api/imports/" + draftId + "/lines/" + lineId(draftId, 1) + "/skip"))
				.andExpect(status().isConflict());

		mvc.perform(post("/api/imports/" + draftId + "/confirm")).andExpect(status().isOk());
		assertThat(stockOf(timingBelt)).isEqualTo(3.0);
		assertThat(stockOf(engineOil)).isEqualTo(1.0);

		// the supplier code was remembered: the same code on the next document is matched automatically
		assertThat(mappings.findBySupplierIdAndSupplierCode(supplier.getId(), "UNK-777")).isPresent();
	}

	/** invoice-valid.xml prints two codes on line 1: the EAN first, then the supplier's own code (CodiceTipo FORNITORE). */
	@Test
	void pendingLineShowsAndRemembersTheSupplierCodeNotTheBarcode() throws Exception {
		mappings.delete(mappings.findBySupplierIdAndSupplierCode(supplier.getId(), "RR-BRK-001").orElseThrow());
		long draftId = uploadOk("invoice-valid.xml");

		mvc.perform(get("/api/imports/" + draftId))
				.andExpect(jsonPath("$.pendingLines").value(1))
				.andExpect(jsonPath("$.lines[0].status").value("PENDING_REVIEW"))
				.andExpect(jsonPath("$.lines[0].supplierCode").value("RR-BRK-001"));

		mvc.perform(post("/api/imports/" + draftId + "/lines/" + lineId(draftId, 0) + "/resolve")
				.contentType(MediaType.APPLICATION_JSON).content("{\"itemId\": " + brakePads.getId() + "}"))
				.andExpect(status().isOk());
		assertThat(mappings.findBySupplierIdAndSupplierCode(supplier.getId(), "RR-BRK-001")).isPresent();
		assertThat(mappings.findBySupplierIdAndSupplierCode(supplier.getId(), "8000000000011")).isEmpty();

		// the next document with the same codes is matched without any review
		mvc.perform(delete("/api/imports/" + draftId)).andExpect(status().isNoContent());
		long again = uploadOk("invoice-valid.xml");
		mvc.perform(get("/api/imports/" + again)).andExpect(jsonPath("$.pendingLines").value(0));
	}

	@Test
	void aLineIsAlsoMatchedThroughItsBarcodeWhenOnlyTheBarcodeIsMapped() throws Exception {
		mappings.delete(mappings.findBySupplierIdAndSupplierCode(supplier.getId(), "RR-BRK-001").orElseThrow());
		mappings.save(new SupplierItemCode(supplier, brakePads, "8000000000011"));
		long draftId = uploadOk("invoice-valid.xml");

		mvc.perform(get("/api/imports/" + draftId))
				.andExpect(jsonPath("$.pendingLines").value(0))
				.andExpect(jsonPath("$.lines[0].status").value("MATCHED"))
				.andExpect(jsonPath("$.lines[0].itemCode").value(brakePads.getCode()));
	}

	@Test
	void creditNoteReversesTheQuantities() throws Exception {
		addStock(brakePads, "5");
		long draftId = uploadOk("credit-note.xml");

		mvc.perform(get("/api/imports/" + draftId))
				.andExpect(jsonPath("$.tipoDocumento").value("TD04"))
				.andExpect(jsonPath("$.lines[0].quantity").value(2))
				.andExpect(jsonPath("$.lines[0].stockDelta").value(-2));

		mvc.perform(post("/api/imports/" + draftId + "/confirm")).andExpect(status().isOk());
		assertThat(stockOf(brakePads)).isEqualTo(3.0);
		mvc.perform(get("/api/inventory/movements?itemId=" + brakePads.getId() + "&sort=id,desc"))
				.andExpect(jsonPath("$.content[0].type").value("OUT"))
				.andExpect(jsonPath("$.content[0].quantity").value(2));
	}

	@Test
	void failedConfirmationChangesNothing() throws Exception {
		// the credit note wants to remove 2, but only 1 is in stock
		addStock(brakePads, "1");
		long draftId = uploadOk("credit-note.xml");

		mvc.perform(post("/api/imports/" + draftId + "/confirm")).andExpect(status().isConflict());

		mvc.perform(get("/api/imports/" + draftId)).andExpect(jsonPath("$.status").value("DRAFT"));
		assertThat(stockOf(brakePads)).isEqualTo(1.0);
	}

	@Test
	void rejectedFilesReturnClearErrors() throws Exception {
		upload(fixture("malformed.xml")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("FatturaPA leggibile")));

		upload(new byte[0]).andExpect(status().isBadRequest());

		upload(fixture("totals-mismatch.xml")).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("I totali non corrispondono")));

		String unknownSupplier = new String(fixture("invoice-valid.xml"), StandardCharsets.UTF_8).replace(vat,
				"39999999999");
		upload(unknownSupplier.getBytes(StandardCharsets.UTF_8)).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("Fornitore sconosciuto")));
	}

	@Test
	void listAndFilterDraftsByStatus() throws Exception {
		long draftId = uploadOk("invoice-valid.xml");

		mvc.perform(get("/api/imports?status=DRAFT&size=100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.id == " + draftId + ")]").isNotEmpty());
		mvc.perform(get("/api/imports?status=CONFIRMED&size=100"))
				.andExpect(jsonPath("$.content[?(@.id == " + draftId + ")]").isEmpty());
		mvc.perform(get("/api/imports/999999")).andExpect(status().isNotFound());
	}
}

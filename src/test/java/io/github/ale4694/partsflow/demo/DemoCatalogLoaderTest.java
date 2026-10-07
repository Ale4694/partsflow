package io.github.ale4694.partsflow.demo;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ale4694.partsflow.TestcontainersConfiguration;
import io.github.ale4694.partsflow.ai.search.FakeEmbeddingModel;
import io.github.ale4694.partsflow.catalog.ItemRepository;
import io.github.ale4694.partsflow.catalog.SupplierItemCodeRepository;
import io.github.ale4694.partsflow.catalog.SupplierRepository;
import io.github.ale4694.partsflow.inventory.InventoryService;
import io.github.ale4694.partsflow.invoiceimport.DraftResponse;
import io.github.ale4694.partsflow.invoiceimport.ImportService;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** The demo profile: loads the catalog once, embeds it in a couple of requests, and never duplicates anything. */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "partsflow.ai.api-key=fake-key")
class DemoCatalogLoaderTest {

	@Autowired
	DemoCatalogLoader loader;
	@Autowired
	ItemRepository items;
	@Autowired
	SupplierRepository suppliers;
	@Autowired
	SupplierItemCodeRepository codes;
	@Autowired
	InventoryService inventory;
	@Autowired
	ImportService importService;
	@Autowired
	FakeEmbeddingModel model;

	@Test
	void loadsAboutAHundredAndTwentyItemsFourSuppliersAndTheirCodes() {
		assertThat(items.count()).isBetween(100L, 150L);
		assertThat(suppliers.count()).isEqualTo(4);
		assertThat(suppliers.findByVatNumber("20000000001")).get().extracting("name").isEqualTo("Ricambi Rossi Srl");
		// the codes of the README sample invoices (src/test/resources/fatturapa) are mapped
		var brake = items.findByCode("FRE-001").orElseThrow();
		assertThat(codes.findBySupplierIdAndSupplierCode(suppliers.findByVatNumber("20000000001").orElseThrow().getId(),
				"RR-BRK-001")).get().extracting(code -> code.getItem().getId()).isEqualTo(brake.getId());
		assertThat(items.findByCode("FLT-001")).get().extracting("description")
				.asString().startsWith("Cartuccia lubrificante motore 1.2 FIRE");
	}

	@Test
	void givesItemsTheirStartingStockAndLeavesSomeBelowTheThreshold() {
		assertThat(inventory.getStock(items.findByCode("LUB-001").orElseThrow().getId()).quantity())
				.isEqualByComparingTo("24");
		assertThat(inventory.listLowStock(org.springframework.data.domain.PageRequest.of(0, 100)).content())
				.isNotEmpty();
	}

	@Test
	void theWholeCatalogWasEmbeddedInTwoRequests() {
		// one event for all new items -> one indexer run -> batches of 100 (the test's loader ran at startup)
		assertThat(model.batchSizes()).hasSizeLessThanOrEqualTo(2);
		assertThat(model.batchSizes().stream().mapToInt(Integer::intValue).sum()).isEqualTo((int) items.count());
	}

	@Test
	void runningTheLoaderAgainChangesNothingAndSendsNothing() throws Exception {
		long itemsBefore = items.count();
		int requestsBefore = model.batchSizes().size();

		loader.run(null);

		assertThat(items.count()).isEqualTo(itemsBefore);
		assertThat(suppliers.count()).isEqualTo(4);
		assertThat(model.batchSizes()).hasSize(requestsBefore);
	}

	@Test
	void theDemoInvoiceImportsAsADraftWithAllLinesWaitingForReview() throws Exception {
		try (InputStream xml = new ClassPathResource("demo/fattura-demo-bianchi.xml").getInputStream()) {
			DraftResponse draft = importService.importFatturaPa(xml);

			assertThat(draft.supplierName()).isEqualTo("Autoforniture Bianchi Spa");
			assertThat(draft.lines()).hasSize(5);
			assertThat(draft.pendingLines()).isEqualTo(5); // supplier codes BX-... are not mapped yet
		}
	}
}

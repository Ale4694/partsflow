package io.github.ale4694.partsflow.invoiceimport;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ale4694.partsflow.TestcontainersConfiguration;
import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.catalog.ItemRepository;
import io.github.ale4694.partsflow.catalog.Supplier;
import io.github.ale4694.partsflow.catalog.SupplierItemCode;
import io.github.ale4694.partsflow.catalog.SupplierItemCodeRepository;
import io.github.ale4694.partsflow.catalog.SupplierRepository;
import io.github.ale4694.partsflow.common.ConflictException;
import io.github.ale4694.partsflow.inventory.InventoryService;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Several people press "confirm" on the same draft at the same moment: stock must change exactly once. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ImportConcurrencyTest {

	private static final int THREADS = 6;

	@Autowired
	ImportService importService;
	@Autowired
	InventoryService inventory;
	@Autowired
	SupplierRepository suppliers;
	@Autowired
	ItemRepository items;
	@Autowired
	SupplierItemCodeRepository mappings;

	@Test
	void doubleConfirmAppliesTheStockOnlyOnce() throws Exception {
		Supplier supplier = suppliers.save(new Supplier("Ricambi Rossi Srl", "39000000001"));
		Item brakePads = items.save(new Item("CONC-IMP-BRK", "Front brake pad set", "PZ", BigDecimal.ZERO));
		Item engineOil = items.save(new Item("CONC-IMP-OIL", "Engine oil", "PZ", BigDecimal.ZERO));
		mappings.save(new SupplierItemCode(supplier, brakePads, "RR-BRK-001"));
		mappings.save(new SupplierItemCode(supplier, engineOil, "RR-OIL-530"));

		String xml;
		try (InputStream in = getClass().getResourceAsStream("/fatturapa/invoice-valid.xml")) {
			xml = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("20000000001", "39000000001");
		}
		Long draftId = importService.importFatturaPa(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
				.id();

		ExecutorService pool = Executors.newFixedThreadPool(THREADS);
		CountDownLatch ready = new CountDownLatch(THREADS);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			results.add(pool.submit(() -> {
				ready.countDown();
				go.await();
				try {
					importService.confirm(draftId);
					return true;
				}
				catch (ConflictException ex) {
					return false;
				}
			}));
		}
		ready.await();
		go.countDown();
		long successes = 0;
		for (Future<Boolean> result : results) {
			if (result.get()) {
				successes++;
			}
		}
		pool.shutdown();

		assertThat(successes).isEqualTo(1);
		assertThat(inventory.getStock(brakePads.getId()).quantity()).isEqualByComparingTo("10");
		assertThat(inventory.getStock(engineOil.getId()).quantity()).isEqualByComparingTo("6");
	}
}

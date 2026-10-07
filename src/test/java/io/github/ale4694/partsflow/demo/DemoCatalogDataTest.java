package io.github.ale4694.partsflow.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

/** Sanity of the demo data file (no Spring, no database). */
class DemoCatalogDataTest {

	private static DemoCatalog catalog;

	@BeforeAll
	static void read() throws Exception {
		try (InputStream in = new ClassPathResource("demo/catalog.json").getInputStream()) {
			catalog = JsonMapper.builder().build().readValue(in, DemoCatalog.class);
		}
	}

	@Test
	void hasAboutAHundredAndTwentyItemsAndFourSuppliers() {
		assertThat(catalog.items()).hasSizeBetween(100, 150);
		assertThat(catalog.suppliers()).hasSize(4);
	}

	@Test
	void itemCodesAreUniqueAndEveryItemIsComplete() {
		Set<String> seen = new HashSet<>();
		for (DemoCatalog.DemoItem item : catalog.items()) {
			assertThat(seen.add(item.code())).as("duplicate code " + item.code()).isTrue();
			assertThat(item.description()).isNotBlank().hasSizeLessThanOrEqualTo(500);
			assertThat(item.unit()).isNotBlank().hasSizeLessThanOrEqualTo(10);
			assertThat(item.reorderThreshold()).isNotNull().isGreaterThanOrEqualTo(java.math.BigDecimal.ZERO);
			assertThat(item.stock()).isNotNull().isGreaterThanOrEqualTo(java.math.BigDecimal.ZERO);
		}
	}

	@Test
	void supplierCodesBelongToKnownSuppliersAndAreUniquePerSupplier() {
		Set<String> vats = new HashSet<>();
		catalog.suppliers().forEach(supplier -> {
			assertThat(supplier.vatNumber()).matches("[A-Z0-9]{8,28}");
			vats.add(supplier.vatNumber());
		});
		Set<String> pairs = new HashSet<>();
		for (DemoCatalog.DemoItem item : catalog.items()) {
			assertThat(item.supplierCodes()).isNotEmpty();
			item.supplierCodes().forEach((vat, code) -> {
				assertThat(vats).contains(vat);
				assertThat(code).isNotBlank().hasSizeLessThanOrEqualTo(100);
				assertThat(pairs.add(vat + "|" + code)).as("duplicate supplier code " + code).isTrue();
			});
		}
	}

	@Test
	void containsTheItemsThatTheReadmeQueriesAndSampleInvoicesNeed() {
		List<String> codes = catalog.items().stream().map(DemoCatalog.DemoItem::code).toList();

		assertThat(codes).contains("FLT-001", "FRE-001", "LUB-001", "MOT-001", "ELE-011", "ELE-002", "CAR-007", "LUB-007");
		assertThat(catalog.items()).anySatisfy(item -> assertThat(item.supplierCodes()).containsEntry("20000000001", "RR-BRK-001"));
		assertThat(catalog.items()).anySatisfy(item -> assertThat(item.supplierCodes()).containsEntry("20000000001", "RR-OIL-530"));
	}
}

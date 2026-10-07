package io.github.ale4694.partsflow.demo;

import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.catalog.ItemRepository;
import io.github.ale4694.partsflow.catalog.Supplier;
import io.github.ale4694.partsflow.catalog.SupplierItemCode;
import io.github.ale4694.partsflow.catalog.SupplierItemCodeRepository;
import io.github.ale4694.partsflow.catalog.SupplierRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the demo suppliers, items and supplier codes in ONE transaction, with the repositories directly (not the
 * services): the services announce every saved item, and one announcement per item would make the semantic search
 * embed the catalog one item at a time. The caller announces all items at once instead, which costs two requests.
 * Idempotent: whatever already exists is left alone.
 */
@Service
@Profile("demo")
class DemoCatalogWriter {

	/** An item that was created now, and the stock it should start with. */
	record Created(long itemId, BigDecimal stock) {
	}

	private final SupplierRepository suppliers;
	private final ItemRepository items;
	private final SupplierItemCodeRepository codes;

	DemoCatalogWriter(SupplierRepository suppliers, ItemRepository items, SupplierItemCodeRepository codes) {
		this.suppliers = suppliers;
		this.items = items;
		this.codes = codes;
	}

	@Transactional
	List<Created> write(DemoCatalog catalog) {
		Map<String, Supplier> byVat = new HashMap<>();
		for (DemoCatalog.DemoSupplier demo : catalog.suppliers()) {
			byVat.put(demo.vatNumber(), suppliers.findByVatNumber(demo.vatNumber())
					.orElseGet(() -> suppliers.save(new Supplier(demo.name(), demo.vatNumber()))));
		}
		List<Created> created = new ArrayList<>();
		for (DemoCatalog.DemoItem demo : catalog.items()) {
			if (items.existsByCode(demo.code())) {
				continue;
			}
			Item item = items.save(new Item(demo.code(), demo.description(), demo.unit(), demo.reorderThreshold()));
			demo.supplierCodes().forEach((vat, supplierCode) -> {
				Supplier supplier = byVat.get(vat);
				if (!codes.existsBySupplierIdAndSupplierCode(supplier.getId(), supplierCode)) {
					codes.save(new SupplierItemCode(supplier, item, supplierCode));
				}
			});
			created.add(new Created(item.getId(), demo.stock()));
		}
		return created;
	}
}

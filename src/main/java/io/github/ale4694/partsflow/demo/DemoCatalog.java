package io.github.ale4694.partsflow.demo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** The content of src/main/resources/demo/catalog.json: invented suppliers and parts. */
record DemoCatalog(List<DemoSupplier> suppliers, List<DemoItem> items) {

	record DemoSupplier(String name, String vatNumber) {
	}

	/** @param supplierCodes the code each supplier uses for this item, keyed by the supplier's VAT number */
	record DemoItem(String code, String description, String unit, BigDecimal reorderThreshold, BigDecimal stock,
			Map<String, String> supplierCodes) {
	}
}

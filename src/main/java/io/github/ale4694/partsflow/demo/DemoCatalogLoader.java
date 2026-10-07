package io.github.ale4694.partsflow.demo;

import io.github.ale4694.partsflow.catalog.ItemChanged;
import io.github.ale4694.partsflow.inventory.InventoryService;
import io.github.ale4694.partsflow.inventory.MovementType;
import io.github.ale4694.partsflow.inventory.StockMovementRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads the demo catalog at startup, only when the Spring profile "demo" is active. Safe to run on every start:
 * items that already exist are skipped, so it never duplicates anything and never overwrites your changes.
 * <p>
 * Order matters for the semantic search: the catalog is saved first, then ONE event announces all new items, so the
 * background indexer embeds them together (about 125 items = two embedding requests, not 125).
 */
@Component
@Profile("demo")
class DemoCatalogLoader implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoCatalogLoader.class);

	private final DemoCatalogWriter writer;
	private final InventoryService inventory;
	private final ApplicationEventPublisher events;

	DemoCatalogLoader(DemoCatalogWriter writer, InventoryService inventory, ApplicationEventPublisher events) {
		this.writer = writer;
		this.inventory = inventory;
		this.events = events;
	}

	@Override
	public void run(ApplicationArguments args) throws IOException {
		DemoCatalog catalog = read();
		List<DemoCatalogWriter.Created> created = writer.write(catalog);
		if (created.isEmpty()) {
			log.info("Demo catalog already loaded: nothing to do");
			return;
		}
		List<StockMovementRequest> stock = created.stream()
				.filter(item -> item.stock().signum() > 0)
				.map(item -> new StockMovementRequest(item.itemId(), MovementType.IN, item.stock(),
						"Giacenza iniziale (dati dimostrativi)", "Catalogo demo"))
				.toList();
		inventory.recordAll(stock);
		events.publishEvent(new ItemChanged(created.stream().map(DemoCatalogWriter.Created::itemId)
				.collect(Collectors.toSet())));
		log.info("Demo catalog loaded: items={} suppliers={}", created.size(), catalog.suppliers().size());
	}

	private DemoCatalog read() throws IOException {
		try (InputStream in = new ClassPathResource("demo/catalog.json").getInputStream()) {
			return JsonMapper.builder().build().readValue(in, DemoCatalog.class);
		}
	}
}

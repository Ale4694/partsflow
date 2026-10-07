package io.github.ale4694.partsflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ale4694.partsflow.TestcontainersConfiguration;
import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.catalog.ItemRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Many threads update the same item at the same moment: no update may be lost and stock never goes negative. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StockConcurrencyTest {

	private static final int THREADS = 8;

	@Autowired
	InventoryService inventory;
	@Autowired
	ItemRepository items;
	@Autowired
	StockMovementRepository movements;

	@Test
	void concurrentInMovementsAreAllApplied() throws Exception {
		Item item = items.save(new Item("CONC-IN", "Concurrency test item", "PZ", BigDecimal.ZERO));

		// The stock row does not exist yet, so this also covers two threads creating it at once
		List<Outcome> outcomes = runConcurrently(THREADS, () -> move(item, MovementType.IN, "1"));

		assertThat(outcomes).allMatch(Outcome::succeeded);
		assertThat(inventory.getStock(item.getId()).quantity()).isEqualByComparingTo(String.valueOf(THREADS));
		assertThat(movements.findByItemId(item.getId(), org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
				.isEqualTo(THREADS);
	}

	@Test
	void concurrentOutMovementsNeverOversell() throws Exception {
		Item item = items.save(new Item("CONC-OUT", "Concurrency test item", "PZ", BigDecimal.ZERO));
		inventory.record(new StockMovementRequest(item.getId(), MovementType.IN, new BigDecimal("5"), "setup", null));

		List<Outcome> outcomes = runConcurrently(THREADS, () -> move(item, MovementType.OUT, "1"));

		long succeeded = outcomes.stream().filter(Outcome::succeeded).count();
		long rejected = outcomes.stream().filter(o -> o.error() instanceof InsufficientStockException).count();
		assertThat(succeeded).isEqualTo(5);
		assertThat(rejected).isEqualTo(THREADS - 5);
		assertThat(inventory.getStock(item.getId()).quantity()).isEqualByComparingTo("0");
	}

	private Outcome move(Item item, MovementType type, String quantity) {
		try {
			inventory.record(new StockMovementRequest(item.getId(), type, new BigDecimal(quantity), "concurrency test", null));
			return new Outcome(null);
		}
		catch (RuntimeException ex) {
			return new Outcome(ex);
		}
	}

	/** Starts all tasks at the same instant to maximise contention. */
	private List<Outcome> runConcurrently(int threads, Callable<Outcome> task) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<Outcome>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			futures.add(pool.submit(() -> {
				ready.countDown();
				go.await();
				return task.call();
			}));
		}
		ready.await();
		go.countDown();
		List<Outcome> outcomes = new ArrayList<>();
		for (Future<Outcome> future : futures) {
			outcomes.add(future.get());
		}
		pool.shutdown();
		return outcomes;
	}

	private record Outcome(RuntimeException error) {
		boolean succeeded() {
			return error == null;
		}
	}
}

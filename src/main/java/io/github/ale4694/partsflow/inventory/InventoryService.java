package io.github.ale4694.partsflow.inventory;

import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.catalog.ItemRepository;
import io.github.ale4694.partsflow.common.ConflictException;
import io.github.ale4694.partsflow.common.PageResponse;
import io.github.ale4694.partsflow.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InventoryService {

	private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

	/**
	 * With N concurrent writers on the same item, each round at least one of them wins, so a writer
	 * loses at most N-1 times. 10 attempts is plenty for a small distributor.
	 */
	static final int MAX_ATTEMPTS = 10;

	private final StockRepository stocks;
	private final StockMovementRepository movements;
	private final ItemRepository items;
	private final TransactionTemplate transaction;
	private final Clock clock;

	public InventoryService(StockRepository stocks, StockMovementRepository movements, ItemRepository items,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.stocks = stocks;
		this.movements = movements;
		this.items = items;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	public StockMovementResponse record(StockMovementRequest request) {
		return recordAll(List.of(request)).getFirst();
	}

	/**
	 * Applies all movements in ONE transaction: either every movement is recorded or none is.
	 * Not annotated with @Transactional on purpose: the retry loop must run outside the transaction,
	 * because an optimistic lock failure is only detected when the transaction commits.
	 */
	public List<StockMovementResponse> recordAll(List<StockMovementRequest> requests) {
		for (int attempt = 1; ; attempt++) {
			try {
				return transaction.execute(status -> requests.stream().map(this::apply).toList());
			}
			catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException ex) {
				// Another transaction changed the same stock row (or created it first): try again on fresh data
				if (attempt == MAX_ATTEMPTS) {
					throw new ConflictException("The stock was modified concurrently, please retry");
				}
				log.debug("Concurrent stock update, retrying (attempt {})", attempt);
			}
		}
	}

	private StockMovementResponse apply(StockMovementRequest request) {
		if (!items.existsById(request.itemId())) {
			throw new ResourceNotFoundException("Item", request.itemId());
		}
		Stock stock = stocks.findById(request.itemId()).orElseGet(() -> new Stock(request.itemId()));
		switch (request.type()) {
			case IN -> stock.add(request.quantity());
			case OUT -> {
				if (!stock.canRemove(request.quantity())) {
					throw new InsufficientStockException(request.itemId(), stock.getQuantity(), request.quantity());
				}
				stock.remove(request.quantity());
			}
		}
		stocks.save(stock);
		StockMovement movement = new StockMovement(request.itemId(), request.type(), request.quantity(),
				request.reason(), request.sourceDocument(), Instant.now(clock));
		return StockMovementResponse.from(movements.save(movement));
	}

	public StockResponse getStock(Long itemId) {
		return transaction.execute(status -> {
			Item item = items.findById(itemId).orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
			BigDecimal quantity = stocks.findById(itemId).map(Stock::getQuantity).orElse(BigDecimal.ZERO);
			return new StockResponse(itemId, item.getCode(), quantity, item.getUnit(), item.getReorderThreshold());
		});
	}

	public PageResponse<StockMovementResponse> listMovements(Long itemId, Pageable pageable) {
		return transaction.execute(status -> {
			if (itemId == null) {
				return PageResponse.from(movements.findAll(pageable).map(StockMovementResponse::from));
			}
			if (!items.existsById(itemId)) {
				throw new ResourceNotFoundException("Item", itemId);
			}
			return PageResponse.from(movements.findByItemId(itemId, pageable).map(StockMovementResponse::from));
		});
	}

	public PageResponse<LowStockItem> listLowStock(Pageable pageable) {
		// Ordering is fixed in the query (by item code); a client-supplied sort is ignored
		Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
		return transaction.execute(status -> PageResponse.from(stocks.findLowStock(unsorted)));
	}
}

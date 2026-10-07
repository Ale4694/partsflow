package io.github.ale4694.partsflow.inventory;

import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.catalog.ItemRepository;
import io.github.ale4694.partsflow.common.PageResponse;
import io.github.ale4694.partsflow.common.ResourceNotFoundException;
import io.github.ale4694.partsflow.common.RetryingTransaction;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

	private final StockRepository stocks;
	private final StockMovementRepository movements;
	private final ItemRepository items;
	private final RetryingTransaction retrying;
	private final Clock clock;

	public InventoryService(StockRepository stocks, StockMovementRepository movements, ItemRepository items,
			RetryingTransaction retrying, Clock clock) {
		this.stocks = stocks;
		this.movements = movements;
		this.items = items;
		this.retrying = retrying;
		this.clock = clock;
	}

	public StockMovementResponse record(StockMovementRequest request) {
		return recordAll(List.of(request)).getFirst();
	}

	/**
	 * Applies all movements in ONE transaction: either every movement is recorded or none is.
	 * Not annotated with @Transactional on purpose: the retry loop must run outside the transaction.
	 */
	public List<StockMovementResponse> recordAll(List<StockMovementRequest> requests) {
		return retrying.execute(() -> applyAll(requests));
	}

	/**
	 * Applies the movements inside the CALLER's transaction, so another feature can commit its own changes
	 * (e.g. "draft confirmed") atomically with the stock update. The caller owns the retry on conflicts.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public List<StockMovementResponse> applyAll(List<StockMovementRequest> requests) {
		return requests.stream().map(this::apply).toList();
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

	@Transactional(readOnly = true)
	public StockResponse getStock(Long itemId) {
		Item item = items.findById(itemId).orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
		BigDecimal quantity = stocks.findById(itemId).map(Stock::getQuantity).orElse(BigDecimal.ZERO);
		return new StockResponse(itemId, item.getCode(), quantity, item.getUnit(), item.getReorderThreshold());
	}

	@Transactional(readOnly = true)
	public PageResponse<StockMovementResponse> listMovements(Long itemId, Pageable pageable) {
		if (itemId == null) {
			return PageResponse.from(movements.findAll(pageable).map(StockMovementResponse::from));
		}
		if (!items.existsById(itemId)) {
			throw new ResourceNotFoundException("Item", itemId);
		}
		return PageResponse.from(movements.findByItemId(itemId, pageable).map(StockMovementResponse::from));
	}

	@Transactional(readOnly = true)
	public PageResponse<LowStockItem> listLowStock(Pageable pageable) {
		// Ordering is fixed in the query (by item code); a client-supplied sort is ignored
		Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
		return PageResponse.from(stocks.findLowStock(unsorted));
	}
}

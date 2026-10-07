package io.github.ale4694.partsflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.ale4694.partsflow.catalog.ItemRepository;
import io.github.ale4694.partsflow.common.ConflictException;
import io.github.ale4694.partsflow.common.ResourceNotFoundException;
import io.github.ale4694.partsflow.common.RetryingTransaction;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

	@Mock
	StockRepository stocks;
	@Mock
	StockMovementRepository movements;
	@Mock
	ItemRepository items;
	@Mock
	PlatformTransactionManager transactionManager;

	InventoryService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
		service = new InventoryService(stocks, movements, items, new RetryingTransaction(transactionManager), clock);
	}

	private StockMovementRequest request(MovementType type, String quantity) {
		return new StockMovementRequest(1L, type, new BigDecimal(quantity), "test", "doc-1");
	}

	private void itemExists() {
		when(items.existsById(1L)).thenReturn(true);
		// lenient: tests that fail before saving the movement never call it
		lenient().when(movements.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void inMovementCreatesStockAndRecordsMovement() {
		itemExists();
		when(stocks.findById(1L)).thenReturn(Optional.empty());

		StockMovementResponse response = service.record(request(MovementType.IN, "10"));

		assertThat(response.type()).isEqualTo(MovementType.IN);
		assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-01-15T10:00:00Z"));
		verify(stocks).save(any(Stock.class));
	}

	@Test
	void outMovementReducesStock() {
		itemExists();
		Stock stock = new Stock(1L);
		stock.add(new BigDecimal("10"));
		when(stocks.findById(1L)).thenReturn(Optional.of(stock));

		service.record(request(MovementType.OUT, "4"));

		assertThat(stock.getQuantity()).isEqualByComparingTo("6");
	}

	@Test
	void outMovementBeyondStockIsRejectedAndNothingIsSaved() {
		when(items.existsById(1L)).thenReturn(true);
		Stock stock = new Stock(1L);
		stock.add(new BigDecimal("3"));
		when(stocks.findById(1L)).thenReturn(Optional.of(stock));

		assertThatThrownBy(() -> service.record(request(MovementType.OUT, "3.001")))
				.isInstanceOf(InsufficientStockException.class)
				.hasMessageContaining("available 3, requested 3.001");
		assertThat(stock.getQuantity()).isEqualByComparingTo("3");
		verify(movements, never()).save(any());
	}

	@Test
	void outMovementOfExactlyTheAvailableQuantityIsAllowed() {
		itemExists();
		Stock stock = new Stock(1L);
		stock.add(new BigDecimal("3"));
		when(stocks.findById(1L)).thenReturn(Optional.of(stock));

		service.record(request(MovementType.OUT, "3"));

		assertThat(stock.getQuantity()).isEqualByComparingTo("0");
	}

	@Test
	void unknownItemIsNotFound() {
		when(items.existsById(1L)).thenReturn(false);

		assertThatThrownBy(() -> service.record(request(MovementType.IN, "1")))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void retriesWhenAnotherTransactionModifiedTheStock() {
		itemExists();
		when(stocks.findById(1L)).thenReturn(Optional.of(new Stock(1L)));
		when(stocks.save(any(Stock.class)))
				.thenThrow(new ObjectOptimisticLockingFailureException(Stock.class, 1L))
				.thenAnswer(inv -> inv.getArgument(0));

		StockMovementResponse response = service.record(request(MovementType.IN, "1"));

		assertThat(response.quantity()).isEqualByComparingTo("1");
		verify(stocks, times(2)).save(any(Stock.class));
	}

	@Test
	void givesUpWithConflictAfterMaxAttempts() {
		itemExists();
		when(stocks.findById(1L)).thenReturn(Optional.of(new Stock(1L)));
		when(stocks.save(any(Stock.class))).thenThrow(new ObjectOptimisticLockingFailureException(Stock.class, 1L));

		assertThatThrownBy(() -> service.record(request(MovementType.IN, "1")))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("concurrently");
		verify(stocks, times(RetryingTransaction.MAX_ATTEMPTS)).save(any(Stock.class));
	}

	@Test
	void batchAppliesMovementsInOrderOnTheSameStock() {
		itemExists();
		Stock stock = new Stock(1L);
		when(stocks.findById(1L)).thenReturn(Optional.of(stock));

		service.recordAll(List.of(request(MovementType.IN, "5"), request(MovementType.OUT, "2")));

		assertThat(stock.getQuantity()).isEqualByComparingTo("3");
	}
}

package io.github.ale4694.partsflow.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.ale4694.partsflow.common.ConflictException;
import io.github.ale4694.partsflow.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

	@Mock
	ItemRepository items;

	@InjectMocks
	ItemService service;

	private final ItemRequest request = new ItemRequest("BRK-001", "Front brake pad set", "PZ", new BigDecimal("5"));

	@Test
	void createSavesNewItem() {
		when(items.existsByCode("BRK-001")).thenReturn(false);
		when(items.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

		ItemResponse response = service.create(request);

		assertThat(response.code()).isEqualTo("BRK-001");
		assertThat(response.reorderThreshold()).isEqualByComparingTo("5");
	}

	@Test
	void createRejectsDuplicateCode() {
		when(items.existsByCode("BRK-001")).thenReturn(true);

		assertThatThrownBy(() -> service.create(request)).isInstanceOf(ConflictException.class);
		verify(items, never()).save(any());
	}

	@Test
	void getUnknownItemThrowsNotFound() {
		when(items.findById(7L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(7L)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void updateRejectsCodeOfAnotherItem() {
		when(items.findById(1L)).thenReturn(Optional.of(new Item("OLD", "Old", "PZ", BigDecimal.ZERO)));
		when(items.existsByCodeAndIdNot("BRK-001", 1L)).thenReturn(true);

		assertThatThrownBy(() -> service.update(1L, request)).isInstanceOf(ConflictException.class);
	}
}

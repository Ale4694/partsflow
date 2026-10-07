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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SupplierItemCodeServiceTest {

	@Mock
	SupplierItemCodeRepository mappings;
	@Mock
	SupplierService supplierService;
	@Mock
	ItemService itemService;

	@Mock
	ApplicationEventPublisher events;

	@InjectMocks
	SupplierItemCodeService service;

	private final Supplier supplier = new Supplier("Ricambi Rossi Srl", "01234567897");
	private final Item item = new Item("BRK-001", "Front brake pad set", "PZ", BigDecimal.ONE);
	private final SupplierItemCodeRequest request = new SupplierItemCodeRequest("RR-4411", 10L);

	@Test
	void createMapsSupplierCodeToItem() {
		when(supplierService.find(1L)).thenReturn(supplier);
		when(itemService.find(10L)).thenReturn(item);
		when(mappings.existsBySupplierIdAndSupplierCode(1L, "RR-4411")).thenReturn(false);
		when(mappings.save(any(SupplierItemCode.class))).thenAnswer(inv -> inv.getArgument(0));

		SupplierItemCodeResponse response = service.create(1L, request);

		assertThat(response.supplierCode()).isEqualTo("RR-4411");
		assertThat(response.itemCode()).isEqualTo("BRK-001");
	}

	@Test
	void createRejectsDuplicateCodeForSameSupplier() {
		when(supplierService.find(1L)).thenReturn(supplier);
		when(itemService.find(10L)).thenReturn(item);
		when(mappings.existsBySupplierIdAndSupplierCode(1L, "RR-4411")).thenReturn(true);

		assertThatThrownBy(() -> service.create(1L, request)).isInstanceOf(ConflictException.class);
		verify(mappings, never()).save(any());
	}

	@Test
	void createFailsWhenSupplierDoesNotExist() {
		when(supplierService.find(1L)).thenThrow(new ResourceNotFoundException("Supplier", 1L));

		assertThatThrownBy(() -> service.create(1L, request)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void getMappingOfAnotherSupplierIsNotFound() {
		when(mappings.findByIdAndSupplierId(5L, 1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(1L, 5L)).isInstanceOf(ResourceNotFoundException.class);
	}
}

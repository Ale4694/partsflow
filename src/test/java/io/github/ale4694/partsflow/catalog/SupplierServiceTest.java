package io.github.ale4694.partsflow.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.ale4694.partsflow.common.ConflictException;
import io.github.ale4694.partsflow.common.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

	@Mock
	SupplierRepository suppliers;

	@InjectMocks
	SupplierService service;

	private final SupplierRequest request = new SupplierRequest("Ricambi Rossi Srl", "01234567897");

	@Test
	void createSavesNewSupplier() {
		when(suppliers.existsByVatNumber("01234567897")).thenReturn(false);
		when(suppliers.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

		SupplierResponse response = service.create(request);

		assertThat(response.name()).isEqualTo("Ricambi Rossi Srl");
		assertThat(response.vatNumber()).isEqualTo("01234567897");
	}

	@Test
	void createRejectsDuplicateVatNumber() {
		when(suppliers.existsByVatNumber("01234567897")).thenReturn(true);

		assertThatThrownBy(() -> service.create(request)).isInstanceOf(ConflictException.class);
		verify(suppliers, never()).save(any());
	}

	@Test
	void getUnknownSupplierThrowsNotFound() {
		when(suppliers.findById(42L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(42L)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void updateChangesFieldsAndChecksDuplicatesOnOtherSuppliers() {
		Supplier existing = new Supplier("Old name", "99999999999");
		when(suppliers.findById(1L)).thenReturn(Optional.of(existing));
		when(suppliers.existsByVatNumberAndIdNot("01234567897", 1L)).thenReturn(false);

		SupplierResponse response = service.update(1L, request);

		assertThat(response.name()).isEqualTo("Ricambi Rossi Srl");
		assertThat(existing.getVatNumber()).isEqualTo("01234567897");
	}

	@Test
	void updateRejectsVatNumberOfAnotherSupplier() {
		when(suppliers.findById(1L)).thenReturn(Optional.of(new Supplier("Old name", "99999999999")));
		when(suppliers.existsByVatNumberAndIdNot("01234567897", 1L)).thenReturn(true);

		assertThatThrownBy(() -> service.update(1L, request)).isInstanceOf(ConflictException.class);
	}
}

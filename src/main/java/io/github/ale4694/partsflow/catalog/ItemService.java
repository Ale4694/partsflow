package io.github.ale4694.partsflow.catalog;

import io.github.ale4694.partsflow.common.ConflictException;
import io.github.ale4694.partsflow.common.PageResponse;
import io.github.ale4694.partsflow.common.ResourceNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ItemService {

	private final ItemRepository items;

	public ItemService(ItemRepository items) {
		this.items = items;
	}

	public ItemResponse create(ItemRequest request) {
		if (items.existsByCode(request.code())) {
			throw new ConflictException("An item with code " + request.code() + " already exists");
		}
		Item item = new Item(request.code(), request.description(), request.unit(), request.reorderThreshold());
		return ItemResponse.from(items.save(item));
	}

	@Transactional(readOnly = true)
	public ItemResponse get(Long id) {
		return ItemResponse.from(find(id));
	}

	@Transactional(readOnly = true)
	public PageResponse<ItemResponse> list(Pageable pageable) {
		return PageResponse.from(items.findAll(pageable).map(ItemResponse::from));
	}

	public ItemResponse update(Long id, ItemRequest request) {
		Item item = find(id);
		if (items.existsByCodeAndIdNot(request.code(), id)) {
			throw new ConflictException("An item with code " + request.code() + " already exists");
		}
		item.update(request.code(), request.description(), request.unit(), request.reorderThreshold());
		return ItemResponse.from(item);
	}

	public void delete(Long id) {
		items.delete(find(id));
		// Flush now so a foreign key violation (item still referenced) surfaces as a 409 here
		items.flush();
	}

	Item find(Long id) {
		return items.findById(id).orElseThrow(() -> new ResourceNotFoundException("Item", id));
	}
}

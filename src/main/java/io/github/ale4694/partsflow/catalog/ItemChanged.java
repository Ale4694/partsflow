package io.github.ale4694.partsflow.catalog;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Published after items were created or changed, or after the supplier codes of items changed (the codes are part of
 * what the semantic search reads about an item). Listeners run after the transaction has committed, so a slow or
 * failing listener can never block or undo saving the item.
 */
public record ItemChanged(Set<Long> itemIds) {

	/** The event for these items; an item that was not saved yet (no id) is left out. */
	public static ItemChanged of(Long... itemIds) {
		return new ItemChanged(Arrays.stream(itemIds).filter(Objects::nonNull).collect(Collectors.toSet()));
	}
}

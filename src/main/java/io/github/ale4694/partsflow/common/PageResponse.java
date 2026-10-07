package io.github.ale4694.partsflow.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable JSON shape for paginated lists, so clients do not depend on Spring's internal Page format. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}
}

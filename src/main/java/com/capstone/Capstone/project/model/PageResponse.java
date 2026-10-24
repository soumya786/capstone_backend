package com.capstone.Capstone.project.model;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	/** Wraps one already-fetched page together with the total number of matching rows. */
	public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
		return new PageResponse<>(content, page, size, totalElements, (int) Math.ceil((double) totalElements / size));
	}
}

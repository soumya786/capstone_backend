package com.capstone.Capstone.project.model;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	/** Slices a fully materialised, already ordered list into the requested zero-based page. */
	public static <T> PageResponse<T> of(List<T> all, int page, int size) {
		int total = all.size();
		long from = (long) page * size;
		List<T> slice = from >= total ? List.of() : all.subList((int) from, (int) Math.min(from + size, total));
		return new PageResponse<>(slice, page, size, total, (int) Math.ceil((double) total / size));
	}
}

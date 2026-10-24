package com.capstone.Capstone.project.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BookSummary(
		UUID id,
		String title,
		List<AuthorSummary> authors,
		String shortDescription,
		String coverImageUrl,
		BookFormat format,
		List<String> genres,
		Money price,
		LocalDate estimatedDeliveryDate) {
}

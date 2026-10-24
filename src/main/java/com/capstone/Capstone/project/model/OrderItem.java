package com.capstone.Capstone.project.model;

import java.util.UUID;

public record OrderItem(
		UUID bookId,
		String title,
		String coverImageUrl,
		int quantity,
		Money unitPrice,
		Money lineTotal) {
}

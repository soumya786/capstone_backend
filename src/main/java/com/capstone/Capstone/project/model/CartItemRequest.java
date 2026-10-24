package com.capstone.Capstone.project.model;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemRequest(
		@NotNull UUID bookId,
		@Min(1) @Max(20) Integer quantity) {

	public CartItemRequest {
		if (quantity == null) {
			quantity = 1;
		}
	}
}

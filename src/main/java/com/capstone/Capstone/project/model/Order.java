package com.capstone.Capstone.project.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record Order(
		UUID id,
		OrderStatus status,
		List<OrderItem> items,
		Address shippingAddress,
		Money total,
		LocalDate estimatedDeliveryDate,
		Instant createdAt) {

	public Order withStatus(OrderStatus newStatus) {
		return new Order(id, newStatus, items, shippingAddress, total, estimatedDeliveryDate, createdAt);
	}
}

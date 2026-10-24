package com.capstone.Capstone.project.model;

import java.time.Instant;
import java.util.UUID;

public record Payment(
		UUID id,
		UUID orderId,
		PaymentMethod method,
		PaymentStatus status,
		Money amount,
		Instant createdAt) {

	public Payment withStatus(PaymentStatus newStatus) {
		return new Payment(id, orderId, method, newStatus, amount, createdAt);
	}
}

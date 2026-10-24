package com.capstone.Capstone.project.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.capstone.Capstone.project.exception.BadRequestException;
import com.capstone.Capstone.project.exception.ConflictException;
import com.capstone.Capstone.project.model.CreatePaymentRequest;
import com.capstone.Capstone.project.model.Order;
import com.capstone.Capstone.project.model.OrderStatus;
import com.capstone.Capstone.project.model.Payment;
import com.capstone.Capstone.project.model.PaymentMethod;
import com.capstone.Capstone.project.model.PaymentStatus;

/**
 * Records payments against orders. There is no real gateway: a payment made with a provider token
 * is treated as authorised, and cash on delivery stays PENDING until delivery.
 */
@Service
public class PaymentService {

	private final OrderService orders;
	private final Map<UUID, List<Payment>> byOrder = new ConcurrentHashMap<>();
	private final Map<String, UUID> idempotency = new ConcurrentHashMap<>();
	private final Map<UUID, Payment> byId = new ConcurrentHashMap<>();

	public PaymentService(OrderService orders) {
		this.orders = orders;
	}

	public synchronized Payment create(String userId, UUID orderId, String idempotencyKey,
			CreatePaymentRequest request) {
		Order order = orders.get(userId, orderId);

		String key = userId + ":" + orderId + ":" + idempotencyKey;
		UUID existing = idempotency.get(key);
		if (existing != null) {
			return byId.get(existing);
		}

		if (order.status() != OrderStatus.PENDING_PAYMENT) {
			throw new ConflictException("Order is not awaiting payment");
		}
		boolean alreadyPaying = byOrder.getOrDefault(orderId, List.of()).stream()
				.anyMatch(p -> p.status() == PaymentStatus.PENDING || p.status() == PaymentStatus.SUCCEEDED);
		if (alreadyPaying) {
			throw new ConflictException("Order already has a payment in progress");
		}

		boolean cod = request.method() == PaymentMethod.CASH_ON_DELIVERY;
		if (!cod && (request.providerToken() == null || request.providerToken().isBlank())) {
			throw new BadRequestException("providerToken is required for this payment method");
		}

		PaymentStatus status = cod ? PaymentStatus.PENDING : PaymentStatus.SUCCEEDED;
		Payment payment = new Payment(UUID.randomUUID(), orderId, request.method(), status, order.total(),
				Instant.now());

		if (status == PaymentStatus.SUCCEEDED) {
			orders.markPaid(userId, orderId);
		}
		byId.put(payment.id(), payment);
		byOrder.merge(orderId, List.of(payment), (a, b) -> {
			var all = new java.util.ArrayList<>(a);
			all.addAll(b);
			return List.copyOf(all);
		});
		idempotency.put(key, payment.id());
		return payment;
	}

	public List<Payment> list(String userId, UUID orderId) {
		orders.get(userId, orderId);
		return byOrder.getOrDefault(orderId, List.of());
	}
}

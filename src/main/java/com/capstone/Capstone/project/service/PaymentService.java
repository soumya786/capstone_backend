package com.capstone.Capstone.project.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.capstone.Capstone.project.exception.BadRequestException;
import com.capstone.Capstone.project.exception.ConflictException;
import com.capstone.Capstone.project.model.CreatePaymentRequest;
import com.capstone.Capstone.project.model.Money;
import com.capstone.Capstone.project.model.Order;
import com.capstone.Capstone.project.model.OrderStatus;
import com.capstone.Capstone.project.model.Payment;
import com.capstone.Capstone.project.model.PaymentMethod;
import com.capstone.Capstone.project.model.PaymentStatus;

/**
 * Records payments against orders in PostgreSQL. There is no real gateway: a payment made with a
 * provider token is treated as authorised, and cash on delivery stays PENDING until delivery.
 * The provider token itself is never stored.
 */
@Service
public class PaymentService {

	private static final String PAYMENT_COLUMNS = "id, order_id, method, status, amount, currency, created_at";

	private static final RowMapper<Payment> PAYMENT_ROW = (rs, n) -> new Payment(
			rs.getObject("id", UUID.class),
			rs.getObject("order_id", UUID.class),
			PaymentMethod.valueOf(rs.getString("method")),
			PaymentStatus.valueOf(rs.getString("status")),
			new Money(rs.getBigDecimal("amount"), rs.getString("currency")),
			rs.getObject("created_at", OffsetDateTime.class).toInstant());

	private final JdbcClient jdbc;
	private final TransactionTemplate tx;
	private final OrderService orders;

	public PaymentService(JdbcClient jdbc, TransactionTemplate tx, OrderService orders) {
		this.jdbc = jdbc;
		this.tx = tx;
		this.orders = orders;
	}

	public Payment create(String userId, UUID orderId, String idempotencyKey, CreatePaymentRequest request) {
		Order order = orders.get(userId, orderId);

		Optional<Payment> existing = findByKey(orderId, idempotencyKey);
		if (existing.isPresent()) {
			return existing.get();
		}

		boolean cod = request.method() == PaymentMethod.CASH_ON_DELIVERY;
		if (!cod && (request.providerToken() == null || request.providerToken().isBlank())) {
			throw new BadRequestException("providerToken is required for this payment method");
		}

		try {
			UUID paymentId = tx.execute(status -> insertPayment(userId, order, idempotencyKey, request, cod));
			return get(paymentId);
		} catch (DuplicateKeyException raced) {
			return findByKey(orderId, idempotencyKey).orElseThrow(() -> raced);
		}
	}

	public List<Payment> list(String userId, UUID orderId) {
		orders.get(userId, orderId);
		return jdbc.sql("SELECT " + PAYMENT_COLUMNS + " FROM payments WHERE order_id = :o"
				+ " ORDER BY created_at, id")
				.param("o", orderId).query(PAYMENT_ROW).list();
	}

	// ---- internals -------------------------------------------------------------------------

	private UUID insertPayment(String userId, Order order, String idempotencyKey, CreatePaymentRequest request,
			boolean cod) {
		// Lock the order row so concurrent payments/cancellations are serialised.
		OrderStatus current = jdbc.sql("SELECT status FROM orders WHERE id = :id AND user_id = :u FOR UPDATE")
				.param("id", order.id()).param("u", userId)
				.query((rs, n) -> OrderStatus.valueOf(rs.getString("status"))).single();
		if (current != OrderStatus.PENDING_PAYMENT) {
			throw new ConflictException("Order is not awaiting payment");
		}
		long inProgress = jdbc.sql("SELECT COUNT(*) FROM payments WHERE order_id = :o"
				+ " AND status IN ('PENDING', 'SUCCEEDED')")
				.param("o", order.id()).query(Long.class).single();
		if (inProgress > 0) {
			throw new ConflictException("Order already has a payment in progress");
		}

		PaymentStatus status = cod ? PaymentStatus.PENDING : PaymentStatus.SUCCEEDED;
		UUID paymentId = UUID.randomUUID();
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		jdbc.sql("INSERT INTO payments (id, order_id, idempotency_key, method, status, amount, currency,"
				+ " created_at) VALUES (:id, :o, :k, :m, :s, :a, :c, :t)")
				.param("id", paymentId).param("o", order.id()).param("k", idempotencyKey)
				.param("m", request.method().name()).param("s", status.name())
				.param("a", order.total().amount()).param("c", order.total().currency())
				.param("t", OffsetDateTime.ofInstant(now, ZoneOffset.UTC)).update();

		if (status == PaymentStatus.SUCCEEDED) {
			jdbc.sql("UPDATE orders SET status = 'PAID' WHERE id = :id").param("id", order.id()).update();
		}
		return paymentId;
	}

	private Optional<Payment> findByKey(UUID orderId, String idempotencyKey) {
		return jdbc.sql("SELECT " + PAYMENT_COLUMNS + " FROM payments WHERE order_id = :o"
				+ " AND idempotency_key = :k")
				.param("o", orderId).param("k", idempotencyKey).query(PAYMENT_ROW).optional();
	}

	private Payment get(UUID paymentId) {
		return jdbc.sql("SELECT " + PAYMENT_COLUMNS + " FROM payments WHERE id = :id")
				.param("id", paymentId).query(PAYMENT_ROW).single();
	}
}

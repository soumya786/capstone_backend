package com.capstone.Capstone.project.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.capstone.Capstone.project.exception.ConflictException;
import com.capstone.Capstone.project.exception.NotFoundException;
import com.capstone.Capstone.project.model.Address;
import com.capstone.Capstone.project.model.BookFormat;
import com.capstone.Capstone.project.model.BookSummary;
import com.capstone.Capstone.project.model.Cart;
import com.capstone.Capstone.project.model.CartItem;
import com.capstone.Capstone.project.model.Money;
import com.capstone.Capstone.project.model.Order;
import com.capstone.Capstone.project.model.OrderItem;
import com.capstone.Capstone.project.model.OrderStatus;
import com.capstone.Capstone.project.model.PageResponse;

/** Orders and order items stored in PostgreSQL. Every query is scoped to the owning user. */
@Service
public class OrderService {

	private static final int DELIVERY_DAYS = 3;

	private static final String ORDER_COLUMNS = "o.id, o.status, o.ship_full_name, o.ship_line1, o.ship_line2,"
			+ " o.ship_city, o.ship_state, o.ship_postal_code, o.ship_phone, o.total_amount, o.currency,"
			+ " o.estimated_delivery_date, o.created_at";

	private record OrderRow(UUID id, OrderStatus status, Address address, Money total, LocalDate eta,
			Instant createdAt) {
	}

	private static final RowMapper<OrderRow> ORDER_ROW = (rs, n) -> new OrderRow(
			rs.getObject("id", UUID.class),
			OrderStatus.valueOf(rs.getString("status")),
			new Address(rs.getString("ship_full_name"), rs.getString("ship_line1"), rs.getString("ship_line2"),
					rs.getString("ship_city"), rs.getString("ship_state"), rs.getString("ship_postal_code"),
					rs.getString("ship_phone")),
			new Money(rs.getBigDecimal("total_amount"), rs.getString("currency")),
			rs.getObject("estimated_delivery_date", LocalDate.class),
			rs.getObject("created_at", OffsetDateTime.class).toInstant());

	private final JdbcClient jdbc;
	private final TransactionTemplate tx;
	private final CartService carts;
	private final CatalogService catalog;

	public OrderService(JdbcClient jdbc, TransactionTemplate tx, CartService carts, CatalogService catalog) {
		this.jdbc = jdbc;
		this.tx = tx;
		this.carts = carts;
		this.catalog = catalog;
	}

	/**
	 * Turns the cart into an order and empties the cart in one transaction. A retry with the same
	 * Idempotency-Key returns the original order instead of creating another.
	 */
	public Order create(String userId, String idempotencyKey, Address shippingAddress) {
		Optional<UUID> existing = findByKey(userId, idempotencyKey);
		if (existing.isPresent()) {
			return get(userId, existing.get());
		}
		try {
			UUID id = tx.execute(status -> insertOrder(userId, idempotencyKey, shippingAddress));
			return get(userId, id);
		} catch (DuplicateKeyException raced) {
			// A concurrent request with the same key won; return its order.
			return get(userId, findByKey(userId, idempotencyKey).orElseThrow(() -> raced));
		}
	}

	public PageResponse<Order> list(String userId, OrderStatus status, int page, int size) {
		String where = " WHERE o.user_id = :u" + (status == null ? "" : " AND o.status = :status");
		JdbcClient.StatementSpec count = jdbc.sql("SELECT COUNT(*) FROM orders o" + where).param("u", userId);
		JdbcClient.StatementSpec rowsQuery = jdbc.sql("SELECT " + ORDER_COLUMNS + " FROM orders o" + where
				+ " ORDER BY o.created_at DESC, o.id LIMIT :limit OFFSET :offset")
				.param("u", userId).param("limit", size).param("offset", (long) page * size);
		if (status != null) {
			count = count.param("status", status.name());
			rowsQuery = rowsQuery.param("status", status.name());
		}
		long total = count.query(Long.class).single();
		List<OrderRow> rows = rowsQuery.query(ORDER_ROW).list();
		return PageResponse.of(withItems(rows), page, size, total);
	}

	public Order get(String userId, UUID orderId) {
		// Same response for "missing" and "someone else's" so order ids cannot be probed.
		OrderRow row = jdbc.sql("SELECT " + ORDER_COLUMNS + " FROM orders o WHERE o.id = :id AND o.user_id = :u")
				.param("id", orderId).param("u", userId).query(ORDER_ROW).optional()
				.orElseThrow(() -> new NotFoundException("Order not found"));
		return withItems(List.of(row)).get(0);
	}

	public Order cancel(String userId, UUID orderId) {
		get(userId, orderId);
		// The status guard in the WHERE clause makes this safe against concurrent payment/cancel.
		int changed = jdbc.sql("UPDATE orders SET status = 'CANCELLED' WHERE id = :id AND user_id = :u"
				+ " AND status IN ('PENDING_PAYMENT', 'PAID')")
				.param("id", orderId).param("u", userId).update();
		if (changed == 0) {
			throw new ConflictException("Order can no longer be cancelled");
		}
		return get(userId, orderId);
	}

	/** Distinct books from past, non-cancelled orders, most recently bought first. */
	public List<BookSummary> buyAgain(String userId, int limit) {
		List<UUID> ids = jdbc.sql("SELECT oi.book_id FROM order_items oi JOIN orders o ON o.id = oi.order_id"
				+ " WHERE o.user_id = :u AND o.status <> 'CANCELLED'"
				+ " GROUP BY oi.book_id ORDER BY MAX(o.created_at) DESC, oi.book_id LIMIT :limit")
				.param("u", userId).param("limit", limit)
				.query((rs, n) -> rs.getObject("book_id", UUID.class)).list();
		return catalog.summaries(ids);
	}

	/** Adds every item of a past order to the cart, all or nothing. */
	public Cart buyOrderAgain(String userId, UUID orderId) {
		Order order = get(userId, orderId);
		tx.executeWithoutResult(status -> {
			for (OrderItem item : order.items()) {
				carts.add(userId, item.bookId(), item.quantity());
			}
		});
		return carts.get(userId);
	}

	// ---- internals -------------------------------------------------------------------------

	private UUID insertOrder(String userId, String idempotencyKey, Address address) {
		// Lock the cart rows so two concurrent checkouts cannot both consume the same cart.
		jdbc.sql("SELECT book_id FROM cart_items WHERE user_id = :u FOR UPDATE").param("u", userId)
				.query((rs, n) -> rs.getObject("book_id", UUID.class)).list();
		Cart cart = carts.get(userId);
		if (cart.items().isEmpty()) {
			throw new ConflictException("Cart is empty");
		}

		boolean physical = cart.items().stream().anyMatch(i -> i.book().format() != BookFormat.EBOOK);
		LocalDate eta = physical ? LocalDate.now().plusDays(DELIVERY_DAYS) : null;
		UUID orderId = UUID.randomUUID();
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

		jdbc.sql("INSERT INTO orders (id, user_id, idempotency_key, status, ship_full_name, ship_line1,"
				+ " ship_line2, ship_city, ship_state, ship_postal_code, ship_phone, total_amount, currency,"
				+ " estimated_delivery_date, created_at) VALUES (:id, :u, :key, 'PENDING_PAYMENT', :name,"
				+ " :l1, :l2, :city, :state, :zip, :phone, :total, :cur, :eta, :created)")
				.param("id", orderId).param("u", userId).param("key", idempotencyKey)
				.param("name", address.fullName()).param("l1", address.line1()).param("l2", address.line2())
				.param("city", address.city()).param("state", address.state())
				.param("zip", address.postalCode()).param("phone", address.phone())
				.param("total", cart.subtotal().amount()).param("cur", cart.subtotal().currency())
				.param("eta", eta).param("created", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
				.update();

		int line = 0;
		for (CartItem item : cart.items()) {
			jdbc.sql("INSERT INTO order_items (order_id, line_no, book_id, title, cover_image_url, quantity,"
					+ " unit_price, line_total, currency) VALUES (:o, :n, :b, :t, :c, :q, :p, :lt, :cur)")
					.param("o", orderId).param("n", ++line).param("b", item.book().id())
					.param("t", item.book().title()).param("c", item.book().coverImageUrl())
					.param("q", item.quantity()).param("p", item.book().price().amount())
					.param("lt", item.lineTotal().amount()).param("cur", item.lineTotal().currency())
					.update();
		}
		carts.clear(userId);
		return orderId;
	}

	private Optional<UUID> findByKey(String userId, String idempotencyKey) {
		return jdbc.sql("SELECT id FROM orders WHERE user_id = :u AND idempotency_key = :k")
				.param("u", userId).param("k", idempotencyKey)
				.query((rs, n) -> rs.getObject("id", UUID.class)).optional();
	}

	private List<Order> withItems(List<OrderRow> rows) {
		if (rows.isEmpty()) {
			return List.of();
		}
		Map<UUID, List<OrderItem>> items = new HashMap<>();
		jdbc.sql("SELECT order_id, book_id, title, cover_image_url, quantity, unit_price, line_total, currency"
				+ " FROM order_items WHERE order_id IN (:ids) ORDER BY order_id, line_no")
				.param("ids", rows.stream().map(OrderRow::id).toList())
				.query((rs, n) -> {
					String currency = rs.getString("currency");
					items.computeIfAbsent(rs.getObject("order_id", UUID.class), k -> new ArrayList<>())
							.add(new OrderItem(rs.getObject("book_id", UUID.class), rs.getString("title"),
									rs.getString("cover_image_url"), rs.getInt("quantity"),
									new Money(rs.getBigDecimal("unit_price"), currency),
									new Money(rs.getBigDecimal("line_total"), currency)));
					return null;
				}).list();
		return rows.stream().map(r -> new Order(r.id(), r.status(), List.copyOf(items.getOrDefault(r.id(), List.of())),
				r.address(), r.total(), r.eta(), r.createdAt())).toList();
	}
}

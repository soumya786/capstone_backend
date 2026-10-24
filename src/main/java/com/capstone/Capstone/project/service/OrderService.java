package com.capstone.Capstone.project.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.capstone.Capstone.project.exception.ConflictException;
import com.capstone.Capstone.project.exception.NotFoundException;
import com.capstone.Capstone.project.model.Address;
import com.capstone.Capstone.project.model.Book;
import com.capstone.Capstone.project.model.BookFormat;
import com.capstone.Capstone.project.model.BookSummary;
import com.capstone.Capstone.project.model.Cart;
import com.capstone.Capstone.project.model.CartItem;
import com.capstone.Capstone.project.model.Order;
import com.capstone.Capstone.project.model.OrderItem;
import com.capstone.Capstone.project.model.OrderStatus;
import com.capstone.Capstone.project.model.PageResponse;

@Service
public class OrderService {

	private static final int DELIVERY_DAYS = 3;

	private record Owned(String userId, Order order) {
	}

	private final CartService carts;
	private final CatalogService catalog;
	private final Map<UUID, Owned> orders = new ConcurrentHashMap<>();
	/** (userId, Idempotency-Key) -> order id, so a retried POST returns the original order. */
	private final Map<String, UUID> idempotency = new ConcurrentHashMap<>();

	public OrderService(CartService carts, CatalogService catalog) {
		this.carts = carts;
		this.catalog = catalog;
	}

	public synchronized Order create(String userId, String idempotencyKey, Address shippingAddress) {
		String key = userId + ":" + idempotencyKey;
		UUID existing = idempotency.get(key);
		if (existing != null) {
			return orders.get(existing).order();
		}

		Cart cart = carts.get(userId);
		if (cart.items().isEmpty()) {
			throw new ConflictException("Cart is empty");
		}

		List<OrderItem> items = new ArrayList<>();
		boolean physical = false;
		for (CartItem line : cart.items()) {
			BookSummary book = line.book();
			items.add(new OrderItem(book.id(), book.title(), book.coverImageUrl(), line.quantity(),
					book.price(), line.lineTotal()));
			physical |= book.format() != BookFormat.EBOOK;
		}
		LocalDate eta = physical ? LocalDate.now().plusDays(DELIVERY_DAYS) : null;
		Order order = new Order(UUID.randomUUID(), OrderStatus.PENDING_PAYMENT, List.copyOf(items),
				shippingAddress, cart.subtotal(), eta, Instant.now());

		orders.put(order.id(), new Owned(userId, order));
		idempotency.put(key, order.id());
		carts.clear(userId);
		return order;
	}

	public PageResponse<Order> list(String userId, OrderStatus status, int page, int size) {
		List<Order> mine = orders.values().stream()
				.filter(o -> o.userId().equals(userId))
				.map(Owned::order)
				.filter(o -> status == null || o.status() == status)
				.sorted(Comparator.comparing(Order::createdAt).reversed())
				.toList();
		return PageResponse.of(mine, page, size);
	}

	public Order get(String userId, UUID orderId) {
		Owned owned = orders.get(orderId);
		// Same response for "missing" and "someone else's" so order ids cannot be probed.
		if (owned == null || !owned.userId().equals(userId)) {
			throw new NotFoundException("Order not found");
		}
		return owned.order();
	}

	public synchronized Order cancel(String userId, UUID orderId) {
		Order order = get(userId, orderId);
		if (order.status() != OrderStatus.PENDING_PAYMENT && order.status() != OrderStatus.PAID) {
			throw new ConflictException("Order can no longer be cancelled");
		}
		return replace(userId, order.withStatus(OrderStatus.CANCELLED));
	}

	/** Called by the payment flow once a payment has succeeded. */
	synchronized Order markPaid(String userId, UUID orderId) {
		Order order = get(userId, orderId);
		if (order.status() != OrderStatus.PENDING_PAYMENT) {
			throw new ConflictException("Order is not awaiting payment");
		}
		return replace(userId, order.withStatus(OrderStatus.PAID));
	}

	/** Distinct books from past orders, most recently bought first. */
	public List<BookSummary> buyAgain(String userId, int limit) {
		Map<UUID, BookSummary> books = new LinkedHashMap<>();
		orders.values().stream()
				.filter(o -> o.userId().equals(userId))
				.map(Owned::order)
				.filter(o -> o.status() != OrderStatus.CANCELLED)
				.sorted(Comparator.comparing(Order::createdAt).reversed())
				.flatMap(o -> o.items().stream())
				.forEach(i -> books.computeIfAbsent(i.bookId(), id -> catalog.getBook(id).toSummary()));
		return books.values().stream().limit(limit).toList();
	}

	public Cart buyOrderAgain(String userId, UUID orderId) {
		Order order = get(userId, orderId);
		Cart cart = carts.get(userId);
		for (OrderItem item : order.items()) {
			Book book = catalog.getBook(item.bookId());
			cart = carts.add(userId, book.id(), item.quantity());
		}
		return cart;
	}

	private Order replace(String userId, Order updated) {
		orders.put(updated.id(), new Owned(userId, updated));
		return updated;
	}
}
